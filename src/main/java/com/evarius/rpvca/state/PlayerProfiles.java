package com.evarius.rpvca.state;

import com.evarius.rpvca.config.PhoneConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PlayerProfiles {
    private final JsonStateStore store;
    private final PhoneConfig config;
    private final Set<String> reservedNumbers;
    private Data data;
    private final Map<UUID, Profile> byPlayer = new LinkedHashMap<>();
    private final Set<String> allocatedNumbers = new HashSet<>();

    public PlayerProfiles(JsonStateStore store, PhoneConfig config, Set<String> reservedNumbers) {
        this.store = store;
        this.config = config;
        this.reservedNumbers = Set.copyOf(reservedNumbers);
        data = store.load("players.json", Data.class, Data::new);
        if (data.profiles == null) {
            data.profiles = new ArrayList<>();
        }
        List<Profile> validProfiles = new ArrayList<>();
        for (Profile profile : data.profiles) {
            if (profile != null && profile.playerId != null && !byPlayer.containsKey(profile.playerId)) {
                if (profile.contacts == null) {
                    profile.contacts = new LinkedHashMap<>();
                }
                if (profile.phoneNumber == null || profile.phoneNumber.isBlank()
                        || reservedNumbers.contains(profile.phoneNumber)
                        || !allocatedNumbers.add(profile.phoneNumber)) {
                    profile.phoneNumber = allocateNumber();
                }
                byPlayer.put(profile.playerId, profile);
                validProfiles.add(profile);
            }
        }
        data.profiles = validProfiles;
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
        created.phoneNumber = allocateNumber();
        created.contacts = new LinkedHashMap<>();
        byPlayer.put(playerId, created);
        data.profiles.add(created);
        save();
        return created;
    }

    public synchronized Profile findByNumber(String number) {
        return byPlayer.values().stream()
                .filter(profile -> number.equals(profile.phoneNumber))
                .findFirst()
                .orElse(null);
    }

    public synchronized void save() {
        store.save("players.json", data);
    }

    private String allocateNumber() {
        long minimum = (long) Math.pow(10, config.numberLength - 1);
        long maximum = (long) Math.pow(10, config.numberLength) - 1L;
        if (data.nextPhoneNumber < minimum || data.nextPhoneNumber > maximum) {
            data.nextPhoneNumber = minimum;
        }
        long start = data.nextPhoneNumber;
        do {
            String candidate = Long.toString(data.nextPhoneNumber++);
            if (data.nextPhoneNumber > maximum) {
                data.nextPhoneNumber = minimum;
            }
            if (!reservedNumbers.contains(candidate) && allocatedNumbers.add(candidate)) {
                return candidate;
            }
        } while (data.nextPhoneNumber != start);
        throw new IllegalStateException("Keine freien Telefonnummern mit " + config.numberLength + " Stellen verfügbar");
    }

    public static final class Data {
        public long nextPhoneNumber;
        public List<Profile> profiles = new ArrayList<>();
    }

    public static final class Profile {
        public UUID playerId;
        public String lastKnownName;
        public String phoneNumber;
        public Map<String, String> contacts = new LinkedHashMap<>();
    }
}
