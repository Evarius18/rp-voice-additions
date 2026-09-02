package com.evarius.rpvca.service;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.api.ContactMutationResult;
import com.evarius.rpvca.api.CallMutationResult;
import com.evarius.rpvca.api.CallerDisplay;
import com.evarius.rpvca.api.HistoryMutationResult;
import com.evarius.rpvca.api.PhoneNumberAllocationResult;
import com.evarius.rpvca.config.EmergencyConfig;
import com.evarius.rpvca.config.PhoneConfig;
import com.evarius.rpvca.item.DeviceItemResolver;
import com.evarius.rpvca.network.CommunicationNetworking;
import com.evarius.rpvca.phone.history.CallDirection;
import com.evarius.rpvca.phone.history.CallHistoryEntryView;
import com.evarius.rpvca.phone.history.CallHistoryStatus;
import com.evarius.rpvca.permissions.EmergencyResponderResolver;
import com.evarius.rpvca.permissions.VanillaPermissionLevels;
import com.evarius.rpvca.state.PlayerProfiles;
import com.evarius.rpvca.state.TowerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative public phone API.
 *
 * <p>All persistent mutations pass through this service. Integrations must never access
 * {@link PlayerProfiles} directly.</p>
 */
public final class PhoneService implements com.evarius.rpvca.api.PhoneApi {
    private final MinecraftServer server;
    private final PhoneConfig config;
    private final EmergencyConfig emergencyConfig;
    private final PlayerProfiles profiles;
    private final TowerRegistry towers;
    private final DeviceItemResolver deviceItems;
    private final EmergencyResponderResolver emergencyResponders = new EmergencyResponderResolver();
    private final long noticeDurationMillis;
    private final Map<UUID, PendingCall> pendingByCaller = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCall> pendingByCallee = new ConcurrentHashMap<>();
    private final Map<UUID, CallSession> activeCalls = new ConcurrentHashMap<>();
    private final Set<UUID> speakerEnabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Notice> notices = new ConcurrentHashMap<>();

    public PhoneService(MinecraftServer server, PhoneConfig config, EmergencyConfig emergencyConfig,
                        PlayerProfiles profiles, TowerRegistry towers, DeviceItemResolver deviceItems) {
        this(server, config, emergencyConfig, profiles, towers, deviceItems, 5);
    }

    public PhoneService(MinecraftServer server, PhoneConfig config, EmergencyConfig emergencyConfig,
                        PlayerProfiles profiles, TowerRegistry towers, DeviceItemResolver deviceItems,
                        int noticeDurationSeconds) {
        this.server = server;
        this.config = config;
        this.emergencyConfig = emergencyConfig;
        this.profiles = profiles;
        this.towers = towers;
        this.deviceItems = deviceItems;
        noticeDurationMillis = Math.max(1, noticeDurationSeconds) * 1_000L;
    }

    public ContactMutationResult upsertContact(ServerPlayerEntity player, String name, String number) {
        if (!sameServer(player)) {
            return ContactMutationResult.NOT_ALLOWED;
        }
        ContactMutationResult result = profiles.upsertContact(player.getUuid(),
                player.getGameProfile().name(), name, number);
        notice(player.getUuid(), "notice.rp-vca.contact." + result.name().toLowerCase(java.util.Locale.ROOT));
        CommunicationNetworking.sync(player);
        return result;
    }

    public ContactMutationResult removeContact(ServerPlayerEntity player, String name) {
        if (!sameServer(player)) {
            return ContactMutationResult.NOT_ALLOWED;
        }
        ContactMutationResult result = profiles.removeContact(player.getUuid(), name);
        notice(player.getUuid(), "notice.rp-vca.contact." + result.name().toLowerCase(java.util.Locale.ROOT));
        CommunicationNetworking.sync(player);
        return result;
    }

    public Map<String, String> getContacts(ServerPlayerEntity player) {
        if (!sameServer(player)) {
            return Map.of();
        }
        return profiles.contacts(player.getUuid());
    }

    public List<CallHistoryEntryView> getCallHistory(ServerPlayerEntity player) {
        if (!sameServer(player)) {
            return List.of();
        }
        return historyFor(player);
    }

    public HistoryMutationResult removeHistoryEntry(ServerPlayerEntity player, UUID entryId) {
        if (!sameServer(player)) {
            return HistoryMutationResult.NOT_ALLOWED;
        }
        HistoryMutationResult result = profiles.removeHistoryEntry(player.getUuid(), entryId);
        notice(player.getUuid(), "notice.rp-vca.history." + result.name().toLowerCase(java.util.Locale.ROOT));
        CommunicationNetworking.sync(player);
        return result;
    }

    public HistoryMutationResult clearOwnCallHistory(ServerPlayerEntity player) {
        if (!sameServer(player)) {
            return HistoryMutationResult.NOT_ALLOWED;
        }
        HistoryMutationResult result = profiles.clearHistory(player.getUuid());
        notice(player.getUuid(), "notice.rp-vca.history." + result.name().toLowerCase(java.util.Locale.ROOT));
        CommunicationNetworking.sync(player);
        return result;
    }

    public HistoryMutationResult clearCallHistory(ServerPlayerEntity administrator, UUID targetPlayerId) {
        if (!sameServer(administrator) || targetPlayerId == null) {
            return HistoryMutationResult.INVALID_REQUEST;
        }
        if (!VanillaPermissionLevels.has(administrator, config.historyAdminPermissionLevel)) {
            return HistoryMutationResult.NOT_ALLOWED;
        }
        HistoryMutationResult result = profiles.clearHistory(targetPlayerId);
        if (result.successful()) {
            RpVoiceAddon.LOGGER.info("Administrator {} hat die Anrufhistorie von {} gelöscht",
                    administrator.getGameProfile().name(), targetPlayerId);
            syncOnline(targetPlayerId);
        }
        return result;
    }

    public HistoryMutationResult clearAllCallHistories(ServerPlayerEntity administrator) {
        if (!sameServer(administrator)) {
            return HistoryMutationResult.INVALID_REQUEST;
        }
        if (!VanillaPermissionLevels.has(administrator, config.historyAdminPermissionLevel)) {
            return HistoryMutationResult.NOT_ALLOWED;
        }
        HistoryMutationResult result = profiles.clearAllHistories();
        if (result.successful()) {
            RpVoiceAddon.LOGGER.warn("Administrator {} hat ALLE RP-VCA-Anrufhistorien gelöscht",
                    administrator.getGameProfile().name());
            server.getPlayerManager().getPlayerList().forEach(CommunicationNetworking::sync);
        }
        return result;
    }

    public PhoneNumberAllocationResult allocateNumber(ServerPlayerEntity player) {
        if (!sameServer(player)) {
            return new PhoneNumberAllocationResult(
                    PhoneNumberAllocationResult.Status.STORAGE_ERROR, "", "");
        }
        PhoneNumberAllocationResult result = profiles.allocateNumber(player.getUuid(),
                player.getGameProfile().name());
        if (result.successful()) {
            CommunicationNetworking.sync(player);
        }
        return result;
    }

    public Optional<String> getAssignedNumber(UUID playerId) {
        return profiles.getAssignedNumber(playerId);
    }

    @Override
    public com.evarius.rpvca.api.PhoneStatusView getStatus(ServerPlayerEntity player) {
        if (!sameServer(player)) {
            return new com.evarius.rpvca.api.PhoneStatusView(
                    "unavailable", null, "", "", false, "", false, false, false, "");
        }
        ClientView view = clientView(player);
        return new com.evarius.rpvca.api.PhoneStatusView(view.state(), view.callId(), view.peer(),
                view.peerNumber(), view.savedContact(), view.number(), view.speaker(),
                view.coverage(), view.phoneHeld(), view.notice());
    }

    @Override
    public List<com.evarius.rpvca.api.EmergencyNumberView> getEmergencyNumbers() {
        if (!emergencyConfig.enabled) {
            return List.of();
        }
        return emergencyConfig.numbers.stream()
                .map(number -> new com.evarius.rpvca.api.EmergencyNumberView(
                        number.number, number.displayName))
                .toList();
    }

    public Optional<UUID> findPlayerIdByName(String playerName) {
        return profiles.findPlayerIdByName(playerName);
    }

    public void initializeProfile(ServerPlayerEntity player) {
        if (sameServer(player)) {
            profile(player);
        }
    }

    public boolean call(ServerPlayerEntity caller, String destination) {
        return startCall(caller, destination).successful();
    }

    @Override
    public CallMutationResult startCall(ServerPlayerEntity caller, String destination) {
        if (!sameServer(caller) || !canUse(caller, true) || isBusy(caller.getUuid())) {
            if (sameServer(caller) && isBusy(caller.getUuid())) {
                caller.sendMessage(Text.translatable("message.rp-vca.phone.busy_self"));
            }
            return CallMutationResult.of(CallMutationResult.Status.NOT_ALLOWED, null);
        }
        PlayerProfiles.Profile callerProfile = profile(caller);
        long startedAt = System.currentTimeMillis();
        Target target = resolveTarget(caller, destination);
        if (target == null) {
            caller.sendMessage(Text.translatable("message.rp-vca.phone.unreachable"));
            recordSingle(callerProfile, CallDirection.OUTGOING, CallHistoryStatus.UNREACHABLE,
                    destination, destination, startedAt, 0L, startedAt);
            CommunicationNetworking.sync(caller);
            return CallMutationResult.of(CallMutationResult.Status.INVALID_NUMBER, null);
        }
        ServerPlayerEntity callee = target.player();
        if (callee == null || !canReceive(callee)) {
            caller.sendMessage(Text.translatable("message.rp-vca.phone.unreachable"));
            recordSingle(callerProfile, CallDirection.OUTGOING, CallHistoryStatus.UNREACHABLE,
                    target.destinationNumber(), target.destinationNumber(), startedAt, 0L, startedAt);
            CommunicationNetworking.sync(caller);
            return CallMutationResult.of(CallMutationResult.Status.UNREACHABLE, null);
        }
        if (callee.getUuid().equals(caller.getUuid())) {
            caller.sendMessage(Text.translatable("message.rp-vca.phone.self_call"));
            recordSingle(callerProfile, CallDirection.OUTGOING, CallHistoryStatus.FAILED,
                    target.destinationNumber(), target.destinationNumber(), startedAt, 0L, startedAt);
            return CallMutationResult.of(CallMutationResult.Status.NOT_ALLOWED, null);
        }
        if (isBusy(callee.getUuid())) {
            caller.sendMessage(Text.translatable("message.rp-vca.phone.busy"));
            recordSingle(callerProfile, CallDirection.OUTGOING, CallHistoryStatus.BUSY,
                    target.destinationNumber(), target.destinationNumber(), startedAt, 0L, startedAt);
            CommunicationNetworking.sync(caller);
            return CallMutationResult.of(CallMutationResult.Status.BUSY, null);
        }
        PlayerProfiles.Profile calleeProfile = profile(callee);
        UUID callId = UUID.randomUUID();
        PendingCall pending = new PendingCall(callId, caller.getUuid(), callee.getUuid(), startedAt,
                callerProfile.phoneNumber, calleeProfile.phoneNumber, target.destinationNumber());
        pendingByCaller.put(caller.getUuid(), pending);
        pendingByCallee.put(callee.getUuid(), pending);
        notices.remove(caller.getUuid());
        notices.remove(callee.getUuid());
        CallerDisplay outgoingDisplay = resolveCallerDisplay(caller, target.destinationNumber());
        CallerDisplay incomingDisplay = resolveCallerDisplay(callee, callerProfile.phoneNumber);
        caller.sendMessage(Text.translatable("message.rp-vca.phone.calling", outgoingDisplay.primaryText()));
        callee.sendMessage(Text.translatable("message.rp-vca.phone.incoming", incomingDisplay.primaryText()));
        syncParticipants(pending);
        return CallMutationResult.of(CallMutationResult.Status.RINGING, callId);
    }

    public boolean answer(ServerPlayerEntity callee) {
        PendingCall pending = callee == null ? null : pendingByCallee.get(callee.getUuid());
        return acceptCall(callee, pending == null ? null : pending.callId()).successful();
    }

    @Override
    public CallMutationResult acceptCall(ServerPlayerEntity callee, UUID callId) {
        if (!sameServer(callee)) {
            return CallMutationResult.of(CallMutationResult.Status.NOT_ALLOWED, callId);
        }
        PendingCall pending = pendingByCallee.get(callee.getUuid());
        if (pending == null) {
            return CallMutationResult.of(CallMutationResult.Status.NOT_FOUND, callId);
        }
        if (callId == null || !pending.callId().equals(callId)) {
            return CallMutationResult.of(CallMutationResult.Status.STALE_CALL, callId);
        }
        pendingByCallee.remove(callee.getUuid(), pending);
        pendingByCaller.remove(pending.callerId());
        ServerPlayerEntity caller = server.getPlayerManager().getPlayer(pending.callerId());
        if (caller == null || !canUse(caller, false) || !canReceive(callee)) {
            long endedAt = System.currentTimeMillis();
            recordPending(pending, CallHistoryStatus.UNREACHABLE, CallHistoryStatus.FAILED, 0L, endedAt);
            callee.sendMessage(Text.translatable("message.rp-vca.phone.no_longer_available"));
            if (caller != null) {
                caller.sendMessage(Text.translatable("message.rp-vca.phone.connection_failed"));
            }
            syncParticipants(pending);
            return CallMutationResult.of(CallMutationResult.Status.UNREACHABLE, callId);
        }
        long answeredAt = System.currentTimeMillis();
        CallSession session = new CallSession(pending.callId(), pending.callerId(), pending.calleeId(),
                pending.createdAt(), answeredAt, pending.callerNumber(), pending.calleeNumber(),
                pending.destinationNumber());
        activeCalls.put(caller.getUuid(), session);
        activeCalls.put(callee.getUuid(), session);
        notices.remove(caller.getUuid());
        notices.remove(callee.getUuid());
        caller.sendMessage(Text.translatable("message.rp-vca.phone.connected",
                resolveCallerDisplay(caller, pending.destinationNumber()).primaryText()));
        callee.sendMessage(Text.translatable("message.rp-vca.phone.connected",
                resolveCallerDisplay(callee, pending.callerNumber()).primaryText()));
        syncParticipants(pending);
        return CallMutationResult.of(CallMutationResult.Status.CONNECTED, callId);
    }

    public boolean decline(ServerPlayerEntity callee) {
        PendingCall pending = callee == null ? null : pendingByCallee.get(callee.getUuid());
        return declineCall(callee, pending == null ? null : pending.callId()).successful();
    }

    @Override
    public CallMutationResult declineCall(ServerPlayerEntity callee, UUID callId) {
        if (!sameServer(callee)) {
            return CallMutationResult.of(CallMutationResult.Status.NOT_ALLOWED, callId);
        }
        PendingCall pending = pendingByCallee.get(callee.getUuid());
        if (pending == null) {
            return CallMutationResult.of(CallMutationResult.Status.NOT_FOUND, callId);
        }
        if (callId == null || !pending.callId().equals(callId)) {
            return CallMutationResult.of(CallMutationResult.Status.STALE_CALL, callId);
        }
        pendingByCallee.remove(callee.getUuid(), pending);
        pendingByCaller.remove(pending.callerId());
        recordPending(pending, CallHistoryStatus.DECLINED, CallHistoryStatus.DECLINED,
                0L, System.currentTimeMillis());
        ServerPlayerEntity caller = server.getPlayerManager().getPlayer(pending.callerId());
        if (caller != null) {
            caller.sendMessage(Text.translatable("message.rp-vca.phone.declined"));
            notice(caller.getUuid(), "notice.rp-vca.phone.declined");
        }
        notice(callee.getUuid(), "notice.rp-vca.phone.declined");
        callee.sendMessage(Text.translatable("message.rp-vca.phone.declined"));
        syncParticipants(pending);
        return CallMutationResult.of(CallMutationResult.Status.DECLINED, callId);
    }

    public boolean hangup(ServerPlayerEntity player) {
        if (!sameServer(player)) {
            return false;
        }
        PendingCall outgoing = pendingByCaller.remove(player.getUuid());
        if (outgoing != null) {
            pendingByCallee.remove(outgoing.calleeId());
            recordPending(outgoing, CallHistoryStatus.CANCELLED, CallHistoryStatus.CANCELLED,
                    0L, System.currentTimeMillis());
            notifyPlayer(outgoing.calleeId(), "message.rp-vca.phone.call_ended");
            notice(outgoing.calleeId(), "notice.rp-vca.phone.ended");
            notice(player.getUuid(), "notice.rp-vca.phone.ended");
            player.sendMessage(Text.translatable("message.rp-vca.phone.call_ended"));
            syncParticipants(outgoing);
            return true;
        }
        PendingCall incoming = pendingByCallee.remove(player.getUuid());
        if (incoming != null) {
            pendingByCaller.remove(incoming.callerId());
            recordPending(incoming, CallHistoryStatus.DECLINED, CallHistoryStatus.DECLINED,
                    0L, System.currentTimeMillis());
            notifyPlayer(incoming.callerId(), "message.rp-vca.phone.call_ended");
            notice(incoming.callerId(), "notice.rp-vca.phone.ended");
            notice(player.getUuid(), "notice.rp-vca.phone.ended");
            player.sendMessage(Text.translatable("message.rp-vca.phone.call_ended"));
            syncParticipants(incoming);
            return true;
        }
        CallSession session = activeCalls.remove(player.getUuid());
        if (session == null) {
            player.sendMessage(Text.translatable("message.rp-vca.phone.no_active_call"));
            return false;
        }
        UUID other = session.other(player.getUuid());
        activeCalls.remove(other);
        speakerEnabled.remove(player.getUuid());
        speakerEnabled.remove(other);
        recordSession(session, CallHistoryStatus.COMPLETED, System.currentTimeMillis());
        notifyPlayer(other, "message.rp-vca.phone.remote_hangup");
        notice(other, "notice.rp-vca.phone.ended");
        notice(player.getUuid(), "notice.rp-vca.phone.ended");
        player.sendMessage(Text.translatable("message.rp-vca.phone.call_ended"));
        syncOnline(other);
        CommunicationNetworking.sync(player);
        return true;
    }

    public boolean toggleSpeaker(ServerPlayerEntity player) {
        if (!sameServer(player) || !activeCalls.containsKey(player.getUuid())) {
            if (sameServer(player)) {
                player.sendMessage(Text.translatable("message.rp-vca.phone.speaker_unavailable"));
            }
            return false;
        }
        boolean enabled;
        if (speakerEnabled.remove(player.getUuid())) {
            enabled = false;
        } else {
            speakerEnabled.add(player.getUuid());
            enabled = true;
        }
        player.sendMessage(Text.translatable(enabled
                ? "message.rp-vca.phone.speaker_on" : "message.rp-vca.phone.speaker_off"));
        CommunicationNetworking.sync(player);
        return enabled;
    }

    public CallSession activeCall(UUID playerId) {
        return activeCalls.get(playerId);
    }

    public boolean isSpeakerEnabled(UUID playerId) {
        return speakerEnabled.contains(playerId);
    }

    public boolean hasCoverage(ServerPlayerEntity player) {
        return !config.requireCoverage || towers.hasCoverage(player);
    }

    public boolean isHoldingUsablePhone(ServerPlayerEntity player) {
        return deviceItems.isHoldingUsablePhone(player, config);
    }

    public boolean canRouteAudio(ServerPlayerEntity player) {
        return hasCoverage(player) && (!config.mustBeHeldForAudio || isHoldingUsablePhone(player));
    }

    public void showNotice(ServerPlayerEntity player, String translationKey) {
        if (!sameServer(player) || translationKey == null
                || !translationKey.matches("[a-z0-9_.-]{1,128}")) return;
        notice(player.getUuid(), translationKey);
        CommunicationNetworking.sync(player);
    }

    public void tick() {
        long timeout = config.ringTimeoutSeconds * 1_000L;
        long now = System.currentTimeMillis();
        pendingByCaller.values().stream()
                .filter(call -> now - call.createdAt() >= timeout)
                .toList()
                .forEach(call -> {
                    if (pendingByCaller.remove(call.callerId(), call)) {
                        pendingByCallee.remove(call.calleeId(), call);
                        recordPending(call, CallHistoryStatus.CANCELLED, CallHistoryStatus.MISSED, 0L, now);
                        notifyPlayer(call.callerId(), "message.rp-vca.phone.no_answer");
                        notifyPlayer(call.calleeId(), "message.rp-vca.phone.missed");
                        notice(call.callerId(), "notice.rp-vca.phone.no_answer");
                        notice(call.calleeId(), "notice.rp-vca.phone.missed");
                        syncParticipants(call);
                    }
                });
        activeCalls.values().stream().distinct().toList().forEach(call -> {
            ServerPlayerEntity first = server.getPlayerManager().getPlayer(call.first());
            ServerPlayerEntity second = server.getPlayerManager().getPlayer(call.second());
            if (first == null || second == null || !hasCoverage(first) || !hasCoverage(second)) {
                endSession(call, "message.rp-vca.phone.network_lost");
            }
        });
    }

    public void onDisconnect(UUID playerId) {
        CallSession call = activeCalls.remove(playerId);
        if (call != null) {
            UUID other = call.other(playerId);
            activeCalls.remove(other);
            speakerEnabled.remove(playerId);
            speakerEnabled.remove(other);
            recordSession(call, CallHistoryStatus.FAILED, System.currentTimeMillis());
            notifyPlayer(other, "message.rp-vca.phone.disconnected");
            notice(other, "notice.rp-vca.phone.disconnected");
            syncOnline(other);
        }
        PendingCall outgoing = pendingByCaller.remove(playerId);
        if (outgoing != null) {
            pendingByCallee.remove(outgoing.calleeId());
            recordPending(outgoing, CallHistoryStatus.CANCELLED, CallHistoryStatus.MISSED,
                    0L, System.currentTimeMillis());
            syncOnline(outgoing.calleeId());
        }
        PendingCall incoming = pendingByCallee.remove(playerId);
        if (incoming != null) {
            pendingByCaller.remove(incoming.callerId());
            recordPending(incoming, CallHistoryStatus.UNREACHABLE, CallHistoryStatus.FAILED,
                    0L, System.currentTimeMillis());
            syncOnline(incoming.callerId());
        }
    }

    public String status(ServerPlayerEntity player) {
        PlayerProfiles.Profile profile = profile(player);
        CallSession call = activeCalls.get(player.getUuid());
        String connection = call == null ? "kein Gespräch" : "verbunden mit " + nameOf(call.other(player.getUuid()));
        return "§6Telefon §7| Nummer: §f" + profiles.formatNumber(profile.phoneNumber) + " §7| " + connection
                + " §7| Netz: " + (hasCoverage(player) ? "§aJa" : "§cNein");
    }

    public ClientView clientView(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PendingCall incoming = pendingByCallee.get(playerId);
        PendingCall outgoing = pendingByCaller.get(playerId);
        CallSession active = activeCalls.get(playerId);
        String state = active != null ? "active" : incoming != null ? "incoming"
                : outgoing != null ? "ringing" : "idle";
        String peerNumber = active != null ? active.otherNumber(playerId)
                : incoming != null ? incoming.callerNumber()
                : outgoing != null ? outgoing.destinationNumber() : "";
        CallerDisplay display = resolveCallerDisplay(player, peerNumber);
        String peer = "idle".equals(state) ? "" : display.primaryText();
        UUID callId = active != null ? active.callId()
                : incoming != null ? incoming.callId() : outgoing != null ? outgoing.callId() : null;
        PlayerProfiles.Profile profile = profile(player);
        Notice notice = notices.get(playerId);
        String noticeText = notice != null && notice.expiresAt() > System.currentTimeMillis() ? notice.text() : "";
        if (notice != null && noticeText.isEmpty()) {
            notices.remove(playerId, notice);
        }
        Map<String, String> contacts = new java.util.LinkedHashMap<>();
        profiles.contacts(playerId).forEach((name, number) ->
                contacts.put(name, profiles.formatNumber(number)));
        return new ClientView(state, callId, peer, display.formattedNumber(),
                display.savedContact(), profiles.formatNumber(profile.phoneNumber),
                isSpeakerEnabled(playerId), hasCoverage(player), isHoldingUsablePhone(player),
                noticeText, Map.copyOf(contacts), historyFor(player));
    }

    @Override
    public CallerDisplay resolveCallerDisplay(ServerPlayerEntity viewer, String callerNumber) {
        if (!sameServer(viewer)) return new CallerDisplay("", "", false, true);
        String normalized = profiles.normalizeNumber(callerNumber).orElse("");
        if (normalized.isBlank()) {
            return new CallerDisplay("", "", false, true);
        }
        String formatted = profiles.formatNumber(normalized);
        String contactName = profiles.contacts(viewer.getUuid()).entrySet().stream()
                .filter(entry -> entry.getValue().equals(normalized))
                .map(Map.Entry::getKey).findFirst().orElse("");
        return contactName.isBlank()
                ? new CallerDisplay(formatted, formatted, false, false)
                : new CallerDisplay(contactName, formatted, true, false);
    }

    private List<CallHistoryEntryView> historyFor(ServerPlayerEntity viewer) {
        return profiles.history(viewer.getUuid()).stream().map(entry -> {
            CallerDisplay display = resolveCallerDisplay(viewer, entry.remoteNumber());
            return new CallHistoryEntryView(entry.entryId(), entry.direction(), entry.status(),
                    entry.localNumber(), entry.remoteNumber(),
                    display.anonymous() ? "" : display.primaryText(),
                    entry.startedAt(), entry.answeredAt(), entry.endedAt(), entry.durationSeconds());
        }).toList();
    }

    private Target resolveTarget(ServerPlayerEntity caller, String rawDestination) {
        String destination = profiles.resolveContact(caller.getUuid(), rawDestination).orElse(rawDestination);
        String normalized = profiles.normalizeNumber(destination).orElse("");
        EmergencyConfig.Number emergency = emergencyConfig.enabled ? emergencyConfig.numbers.stream()
                .filter(number -> number.number.equals(normalized)).findFirst().orElse(null) : null;
        if (emergency != null) {
            ServerPlayerEntity responder = server.getPlayerManager().getPlayerList().stream()
                    .filter(player -> !player.getUuid().equals(caller.getUuid()))
                    .filter(player -> emergencyResponders.isEligible(player, emergency))
                    .filter(player -> !isBusy(player.getUuid()) && canReceive(player))
                    .min(Comparator.comparingDouble(player -> player.squaredDistanceTo(caller)))
                    .orElse(null);
            return new Target(responder, emergency.number);
        }
        PlayerProfiles.Profile byNumber = profiles.findByNumber(destination);
        if (byNumber != null) {
            return new Target(server.getPlayerManager().getPlayer(byNumber.playerId), byNumber.phoneNumber);
        }
        ServerPlayerEntity online = server.getPlayerManager().getPlayer(destination);
        if (online != null) {
            PlayerProfiles.Profile profile = profile(online);
            return new Target(online, profile.phoneNumber);
        }
        Optional<UUID> knownPlayer = profiles.findPlayerIdByName(destination);
        if (knownPlayer.isPresent()) {
            String number = profiles.getAssignedNumber(knownPlayer.get()).orElse(normalized);
            return new Target(null, number);
        }
        return null;
    }

    private boolean canUse(ServerPlayerEntity player, boolean report) {
        if (!config.enabled) {
            if (report) player.sendMessage(Text.translatable("message.rp-vca.phone.disabled"));
            return false;
        }
        if (config.requirePhoneItem && !deviceItems.hasPhone(player)) {
            if (report) player.sendMessage(Text.translatable("message.rp-vca.phone.device_required"));
            return false;
        }
        if (!hasCoverage(player)) {
            if (report) player.sendMessage(Text.translatable("message.rp-vca.phone.no_network"));
            return false;
        }
        return true;
    }

    private boolean canReceive(ServerPlayerEntity player) {
        return canUse(player, false);
    }

    private boolean isBusy(UUID playerId) {
        return activeCalls.containsKey(playerId)
                || pendingByCaller.containsKey(playerId) || pendingByCallee.containsKey(playerId);
    }

    private void endSession(CallSession call, String message) {
        activeCalls.remove(call.first());
        activeCalls.remove(call.second());
        speakerEnabled.remove(call.first());
        speakerEnabled.remove(call.second());
        recordSession(call, CallHistoryStatus.FAILED, System.currentTimeMillis());
        notifyPlayer(call.first(), message);
        notifyPlayer(call.second(), message);
        notice(call.first(), "notice.rp-vca.phone.disconnected");
        notice(call.second(), "notice.rp-vca.phone.disconnected");
        syncOnline(call.first());
        syncOnline(call.second());
    }

    private void recordSingle(PlayerProfiles.Profile local, CallDirection direction, CallHistoryStatus status,
                              String remoteNumber, String remoteName, long startedAt,
                              long answeredAt, long endedAt) {
        String normalizedRemote = profiles.normalizeNumber(remoteNumber).orElse("");
        profiles.appendHistory(List.of(new PlayerProfiles.HistoryDraft(local.playerId, direction, status,
                local.phoneNumber, normalizedRemote, remoteName == null ? "" : remoteName,
                startedAt, answeredAt, endedAt)));
    }

    private void recordPending(PendingCall call, CallHistoryStatus callerStatus,
                               CallHistoryStatus calleeStatus, long answeredAt, long endedAt) {
        profiles.appendHistory(List.of(
                new PlayerProfiles.HistoryDraft(call.callerId(), CallDirection.OUTGOING, callerStatus,
                        call.callerNumber(), call.destinationNumber(), call.destinationNumber(),
                        call.createdAt(), answeredAt, endedAt),
                new PlayerProfiles.HistoryDraft(call.calleeId(), CallDirection.INCOMING, calleeStatus,
                        call.calleeNumber(), call.callerNumber(), call.callerNumber(),
                        call.createdAt(), answeredAt, endedAt)
        ));
    }

    private void recordSession(CallSession call, CallHistoryStatus status, long endedAt) {
        profiles.appendHistory(List.of(
                new PlayerProfiles.HistoryDraft(call.first(), CallDirection.OUTGOING, status,
                        call.firstNumber(), call.destinationNumber(), call.destinationNumber(),
                        call.startedAt(), call.answeredAt(), endedAt),
                new PlayerProfiles.HistoryDraft(call.second(), CallDirection.INCOMING, status,
                        call.secondNumber(), call.firstNumber(), call.firstNumber(),
                        call.startedAt(), call.answeredAt(), endedAt)
        ));
    }

    private PlayerProfiles.Profile profile(ServerPlayerEntity player) {
        return profiles.getOrCreate(player.getUuid(), player.getGameProfile().name());
    }

    private boolean sameServer(ServerPlayerEntity player) {
        return player != null && player.getEntityWorld().getServer() == server && server.isOnThread();
    }

    private void notifyPlayer(UUID playerId, String message) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Text.translatable(message));
        }
    }

    private void syncOnline(UUID playerId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            CommunicationNetworking.sync(player);
        }
    }

    private void syncParticipants(PendingCall call) {
        syncOnline(call.callerId());
        syncOnline(call.calleeId());
    }

    private String nameOf(UUID playerId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        return player == null ? profiles.displayName(playerId) : player.getGameProfile().name();
    }

    private void notice(UUID playerId, String text) {
        notices.put(playerId, new Notice(text, System.currentTimeMillis() + noticeDurationMillis));
    }

    private record PendingCall(UUID callId, UUID callerId, UUID calleeId, long createdAt,
                               String callerNumber, String calleeNumber, String destinationNumber) {
    }

    private record Target(ServerPlayerEntity player, String destinationNumber) {
    }

    public record CallSession(UUID callId, UUID first, UUID second, long startedAt, long answeredAt,
                              String firstNumber, String secondNumber, String destinationNumber) {
        public CallSession(UUID first, UUID second, String label) {
            this(UUID.randomUUID(), first, second, 0L, 0L, "", "", "");
        }

        public UUID other(UUID playerId) {
            if (first.equals(playerId)) {
                return second;
            }
            if (second.equals(playerId)) {
                return first;
            }
            throw new IllegalArgumentException("Spieler ist kein Teilnehmer dieses Gesprächs: " + playerId);
        }

        public String otherNumber(UUID playerId) {
            if (first.equals(playerId)) return secondNumber;
            if (second.equals(playerId)) return firstNumber;
            return "";
        }
    }

    public record ClientView(String state, UUID callId, String peer, String peerNumber,
                             boolean savedContact, String number, boolean speaker,
                             boolean coverage, boolean phoneHeld, String notice, Map<String, String> contacts,
                             List<CallHistoryEntryView> history) {
    }

    private record Notice(String text, long expiresAt) {
    }
}
