package com.evarius.rpvca.state;

import com.evarius.rpvca.api.ContactMutationResult;
import com.evarius.rpvca.api.HistoryMutationResult;
import com.evarius.rpvca.api.PhoneNumberAllocationResult;
import com.evarius.rpvca.config.PhoneConfig;
import com.evarius.rpvca.phone.PhoneNumberService;
import com.evarius.rpvca.phone.history.CallDirection;
import com.evarius.rpvca.phone.history.CallHistoryEntryView;
import com.evarius.rpvca.phone.history.CallHistoryStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sole owner of persistent phone profile data.
 *
 * <p>Mutation methods return stable result values and snapshots. The mutable Gson objects
 * below are never exposed through the public RP-VCA API.</p>
 */
public final class PlayerProfiles {
    private final JsonStateStore store;
    private final PhoneConfig config;
    private final Set<String> reservedNumbers;
    private final PhoneNumberService numbers;
    private final Data data;
    private final Map<UUID, Profile> byPlayer = new LinkedHashMap<>();
    private final Set<String> allocatedNumbers = new HashSet<>();

    public PlayerProfiles(JsonStateStore store, PhoneConfig config, Set<String> reservedNumbers) {
        this.store = store;
        this.config = config;
        this.reservedNumbers = Set.copyOf(reservedNumbers);
        this.numbers = new PhoneNumberService(config, reservedNumbers);
        Data loaded = store.load("players.json", Data.class, Data::new);
        data = loaded == null ? new Data() : loaded;
        if (data.profiles == null) {
            data.profiles = new ArrayList<>();
        }
        migrateAndIndex();
    }

    private void migrateAndIndex() {
        boolean migrated = data.schemaVersion < 2;
        List<Profile> validProfiles = new ArrayList<>();
        for (Profile profile : data.profiles) {
            if (profile == null || profile.playerId == null || byPlayer.containsKey(profile.playerId)) {
                migrated = true;
                continue;
            }
            if (profile.contacts == null) {
                profile.contacts = new LinkedHashMap<>();
                migrated = true;
            }
            if (profile.callHistory == null) {
                profile.callHistory = new ArrayList<>();
                migrated = true;
            }
            if (profile.callHistory.removeIf(entry -> entry == null || entry.entryId == null)) {
                migrated = true;
            }
            String normalized = numbers.normalize(profile.phoneNumber).orElse("");
            if (normalized.isBlank() || reservedNumbers.contains(normalized) || !allocatedNumbers.add(normalized)) {
                profile.phoneNumber = allocateNumberOrThrow();
                migrated = true;
            } else {
                migrated |= !normalized.equals(profile.phoneNumber);
                profile.phoneNumber = normalized;
            }
            int historySize = profile.callHistory.size();
            trimHistory(profile);
            migrated |= historySize != profile.callHistory.size();
            byPlayer.put(profile.playerId, profile);
            validProfiles.add(profile);
        }
        migrated |= validProfiles.size() != data.profiles.size();
        data.profiles = validProfiles;
        data.schemaVersion = 2;
        if (migrated && !save()) {
            com.evarius.rpvca.RpVoiceAddon.LOGGER.warn(
                    "Die Migration von players.json konnte nicht sofort gespeichert werden");
        }
    }

    public synchronized Profile getOrCreate(UUID playerId, String playerName) {
        Profile existing = byPlayer.get(playerId);
        if (existing != null) {
            existing.lastKnownName = playerName;
            return existing;
        }
        Profile created = new Profile();
        created.playerId = playerId;
        created.lastKnownName = playerName;
        created.phoneNumber = allocateNumberOrThrow();
        byPlayer.put(playerId, created);
        data.profiles.add(created);
        if (!save()) {
            data.profiles.remove(created);
            byPlayer.remove(playerId);
            allocatedNumbers.remove(created.phoneNumber);
            throw new IllegalStateException("Telefonprofil konnte nicht persistent gespeichert werden");
        }
        return created;
    }

    public synchronized Profile findByNumber(String input) {
        Optional<String> normalized = numbers.normalize(input);
        if (normalized.isEmpty()) {
            return null;
        }
        return byPlayer.values().stream()
                .filter(profile -> normalized.get().equals(profile.phoneNumber))
                .findFirst().orElse(null);
    }

    public synchronized Optional<UUID> findPlayerIdByName(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return byPlayer.values().stream()
                .filter(profile -> profile.lastKnownName != null && profile.lastKnownName.equalsIgnoreCase(name.trim()))
                .map(profile -> profile.playerId).findFirst();
    }

    public synchronized String displayName(UUID playerId) {
        Profile profile = byPlayer.get(playerId);
        return profile == null || profile.lastKnownName == null || profile.lastKnownName.isBlank()
                ? "unbekannt" : profile.lastKnownName;
    }

    public synchronized Optional<String> getAssignedNumber(UUID playerId) {
        Profile profile = byPlayer.get(playerId);
        return profile == null ? Optional.empty() : Optional.of(profile.phoneNumber);
    }

    public synchronized String formattedNumber(UUID playerId) {
        return getAssignedNumber(playerId).map(numbers::format).orElse("");
    }

    public synchronized String formatNumber(String normalized) {
        return numbers.format(normalized == null ? "" : normalized);
    }

    public synchronized Optional<String> normalizeNumber(String input) {
        return numbers.normalize(input);
    }

    public synchronized PhoneNumberAllocationResult allocateNumber(UUID playerId, String playerName) {
        Profile existing = byPlayer.get(playerId);
        if (existing != null && existing.phoneNumber != null && !existing.phoneNumber.isBlank()) {
            return new PhoneNumberAllocationResult(PhoneNumberAllocationResult.Status.ALREADY_ASSIGNED,
                    existing.phoneNumber, numbers.format(existing.phoneNumber));
        }
        PhoneNumberAllocationResult allocation = allocateNumberResult();
        if (!allocation.successful()) {
            return allocation;
        }
        Profile profile = existing == null ? new Profile() : existing;
        profile.playerId = playerId;
        profile.lastKnownName = playerName;
        profile.phoneNumber = allocation.normalizedNumber();
        if (existing == null) {
            byPlayer.put(playerId, profile);
            data.profiles.add(profile);
        }
        allocatedNumbers.add(profile.phoneNumber);
        if (!save()) {
            allocatedNumbers.remove(profile.phoneNumber);
            if (existing == null) {
                byPlayer.remove(playerId);
                data.profiles.remove(profile);
            } else {
                profile.phoneNumber = null;
            }
            return new PhoneNumberAllocationResult(PhoneNumberAllocationResult.Status.STORAGE_ERROR, "", "");
        }
        return allocation;
    }

    public synchronized Map<String, String> contacts(UUID playerId) {
        Profile profile = byPlayer.get(playerId);
        return profile == null ? Map.of() : Map.copyOf(profile.contacts);
    }

    public synchronized Optional<String> resolveContact(UUID playerId, String name) {
        if (name == null) {
            return Optional.empty();
        }
        Profile profile = byPlayer.get(playerId);
        if (profile == null) {
            return Optional.empty();
        }
        return profile.contacts.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name.trim()))
                .map(Map.Entry::getValue).findFirst();
    }

    public synchronized ContactMutationResult upsertContact(UUID playerId, String playerName,
                                                            String rawName, String rawNumber) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isBlank() || name.length() > config.maxContactNameLength
                || name.chars().anyMatch(Character::isISOControl)) {
            return ContactMutationResult.INVALID_NAME;
        }
        Optional<String> normalized = numbers.normalize(rawNumber);
        if (normalized.isEmpty()) {
            return ContactMutationResult.INVALID_NUMBER;
        }
        Profile profile;
        try {
            profile = getOrCreate(playerId, playerName);
        } catch (IllegalStateException exception) {
            return ContactMutationResult.STORAGE_ERROR;
        }
        String existingKey = profile.contacts.keySet().stream()
                .filter(key -> key.equalsIgnoreCase(name)).findFirst().orElse(null);
        boolean duplicate = profile.contacts.entrySet().stream()
                .anyMatch(entry -> !entry.getKey().equalsIgnoreCase(name)
                        && entry.getValue().equals(normalized.get()));
        if (duplicate) {
            return ContactMutationResult.DUPLICATE_NUMBER;
        }
        if (existingKey == null && profile.contacts.size() >= config.maxContactsPerPlayer) {
            return ContactMutationResult.LIMIT_REACHED;
        }
        boolean created = existingKey == null;
        Map<String, String> before = new LinkedHashMap<>(profile.contacts);
        if (existingKey != null && !existingKey.equals(name)) {
            profile.contacts.remove(existingKey);
        }
        profile.contacts.put(name, normalized.get());
        if (!save()) {
            profile.contacts = before;
            return ContactMutationResult.STORAGE_ERROR;
        }
        return created ? ContactMutationResult.CREATED : ContactMutationResult.UPDATED;
    }

    public synchronized ContactMutationResult removeContact(UUID playerId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isBlank() || name.length() > config.maxContactNameLength) {
            return ContactMutationResult.INVALID_NAME;
        }
        Profile profile = byPlayer.get(playerId);
        if (profile == null) {
            return ContactMutationResult.NOT_FOUND;
        }
        String key = profile.contacts.keySet().stream()
                .filter(existing -> existing.equalsIgnoreCase(name)).findFirst().orElse(null);
        if (key == null) {
            return ContactMutationResult.NOT_FOUND;
        }
        String removed = profile.contacts.remove(key);
        if (!save()) {
            profile.contacts.put(key, removed);
            return ContactMutationResult.STORAGE_ERROR;
        }
        return ContactMutationResult.REMOVED;
    }

    public synchronized boolean appendHistory(List<HistoryDraft> drafts) {
        if (config.maxCallHistoryEntries <= 0) {
            return true;
        }
        Map<UUID, List<PersistentHistoryEntry>> before = new LinkedHashMap<>();
        for (HistoryDraft draft : drafts) {
            Profile profile = byPlayer.get(draft.ownerId());
            if (profile == null) {
                continue;
            }
            before.computeIfAbsent(profile.playerId, ignored -> new ArrayList<>(profile.callHistory));
            PersistentHistoryEntry entry = new PersistentHistoryEntry();
            entry.entryId = UUID.randomUUID();
            entry.direction = draft.direction();
            entry.status = draft.status();
            entry.localNumber = draft.localNumber();
            entry.remoteNumber = draft.remoteNumber();
            entry.remoteDisplayName = draft.remoteDisplayName();
            entry.startedAt = draft.startedAt();
            entry.answeredAt = draft.answeredAt();
            entry.endedAt = draft.endedAt();
            entry.durationSeconds = draft.answeredAt() > 0L
                    ? Math.max(0L, (draft.endedAt() - draft.answeredAt()) / 1_000L) : 0L;
            profile.callHistory.add(entry);
            trimHistory(profile);
        }
        if (save()) {
            return true;
        }
        before.forEach((playerId, entries) -> byPlayer.get(playerId).callHistory = entries);
        return false;
    }

    public synchronized List<CallHistoryEntryView> history(UUID playerId) {
        Profile profile = byPlayer.get(playerId);
        if (profile == null) {
            return List.of();
        }
        return profile.callHistory.stream()
                .sorted(Comparator.comparingLong((PersistentHistoryEntry entry) -> entry.startedAt).reversed())
                .map(entry -> entry.view(numbers))
                .toList();
    }

    public synchronized HistoryMutationResult removeHistoryEntry(UUID playerId, UUID entryId) {
        if (entryId == null) {
            return HistoryMutationResult.INVALID_REQUEST;
        }
        Profile profile = byPlayer.get(playerId);
        if (profile == null) {
            return HistoryMutationResult.NOT_FOUND;
        }
        List<PersistentHistoryEntry> before = new ArrayList<>(profile.callHistory);
        if (!profile.callHistory.removeIf(entry -> entry.entryId.equals(entryId))) {
            return HistoryMutationResult.NOT_FOUND;
        }
        if (!save()) {
            profile.callHistory = before;
            return HistoryMutationResult.STORAGE_ERROR;
        }
        return HistoryMutationResult.REMOVED;
    }

    public synchronized HistoryMutationResult clearHistory(UUID playerId) {
        Profile profile = byPlayer.get(playerId);
        if (profile == null) {
            return HistoryMutationResult.NOT_FOUND;
        }
        List<PersistentHistoryEntry> before = new ArrayList<>(profile.callHistory);
        profile.callHistory.clear();
        if (!save()) {
            profile.callHistory = before;
            return HistoryMutationResult.STORAGE_ERROR;
        }
        return HistoryMutationResult.CLEARED;
    }

    public synchronized HistoryMutationResult clearAllHistories() {
        Map<UUID, List<PersistentHistoryEntry>> before = new LinkedHashMap<>();
        byPlayer.values().forEach(profile -> {
            before.put(profile.playerId, new ArrayList<>(profile.callHistory));
            profile.callHistory.clear();
        });
        if (!save()) {
            before.forEach((playerId, entries) -> byPlayer.get(playerId).callHistory = entries);
            return HistoryMutationResult.STORAGE_ERROR;
        }
        return HistoryMutationResult.CLEARED;
    }

    public synchronized boolean save() {
        return store.save("players.json", data);
    }

    private void trimHistory(Profile profile) {
        profile.callHistory.sort(Comparator.comparingLong((PersistentHistoryEntry entry) -> entry.startedAt).reversed());
        if (profile.callHistory.size() > config.maxCallHistoryEntries) {
            profile.callHistory.subList(config.maxCallHistoryEntries, profile.callHistory.size()).clear();
        }
    }

    private String allocateNumberOrThrow() {
        PhoneNumberAllocationResult result = allocateNumberResult();
        if (!result.successful()) {
            throw new IllegalStateException("Telefonnummer konnte nicht vergeben werden: " + result.status());
        }
        allocatedNumbers.add(result.normalizedNumber());
        return result.normalizedNumber();
    }

    private PhoneNumberAllocationResult allocateNumberResult() {
        if (config.phoneNumberGeneration.enabled) {
            return numbers.generate(allocatedNumbers);
        }
        long minimum = powerOfTen(config.numberLength - 1);
        long maximum = powerOfTen(config.numberLength) - 1L;
        if (data.nextPhoneNumber < minimum || data.nextPhoneNumber > maximum) {
            data.nextPhoneNumber = minimum;
        }
        long start = data.nextPhoneNumber;
        do {
            String candidate = Long.toString(data.nextPhoneNumber++);
            if (data.nextPhoneNumber > maximum) {
                data.nextPhoneNumber = minimum;
            }
            if (!reservedNumbers.contains(candidate) && !allocatedNumbers.contains(candidate)) {
                return new PhoneNumberAllocationResult(PhoneNumberAllocationResult.Status.CREATED,
                        candidate, numbers.format(candidate));
            }
        } while (data.nextPhoneNumber != start);
        return new PhoneNumberAllocationResult(PhoneNumberAllocationResult.Status.NUMBER_SPACE_EXHAUSTED, "", "");
    }

    private static long powerOfTen(int digits) {
        long value = 1L;
        for (int i = 0; i < digits; i++) {
            value *= 10L;
        }
        return value;
    }

    public static final class Data {
        public int schemaVersion = 2;
        public long nextPhoneNumber;
        public List<Profile> profiles = new ArrayList<>();
    }

    public static final class Profile {
        public UUID playerId;
        public String lastKnownName;
        public String phoneNumber;
        public Map<String, String> contacts = new LinkedHashMap<>();
        public List<PersistentHistoryEntry> callHistory = new ArrayList<>();
    }

    public static final class PersistentHistoryEntry {
        public UUID entryId;
        public CallDirection direction = CallDirection.OUTGOING;
        public CallHistoryStatus status = CallHistoryStatus.FAILED;
        public String localNumber = "";
        public String remoteNumber = "";
        public String remoteDisplayName = "";
        public long startedAt;
        public long answeredAt;
        public long endedAt;
        public long durationSeconds;

        private CallHistoryEntryView view(PhoneNumberService numbers) {
            return new CallHistoryEntryView(entryId, direction, status, numbers.format(localNumber),
                    numbers.format(remoteNumber),
                    remoteDisplayName, startedAt, answeredAt, endedAt, durationSeconds);
        }
    }

    public record HistoryDraft(
            UUID ownerId,
            CallDirection direction,
            CallHistoryStatus status,
            String localNumber,
            String remoteNumber,
            String remoteDisplayName,
            long startedAt,
            long answeredAt,
            long endedAt
    ) {
    }
}
