package com.evarius.rpvca.service;

import com.evarius.rpvca.config.EmergencyConfig;
import com.evarius.rpvca.config.PhoneConfig;
import com.evarius.rpvca.item.DeviceItemResolver;
import com.evarius.rpvca.state.PlayerProfiles;
import com.evarius.rpvca.state.TowerRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhoneService {
    private final MinecraftServer server;
    private final PhoneConfig config;
    private final EmergencyConfig emergencyConfig;
    private final PlayerProfiles profiles;
    private final TowerRegistry towers;
    private final DeviceItemResolver deviceItems;
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
        this.noticeDurationMillis = Math.max(1, noticeDurationSeconds) * 1_000L;
    }

    public PlayerProfiles.Profile profile(ServerPlayerEntity player) {
        return profiles.getOrCreate(player.getUuid(), player.getGameProfile().getName());
    }

    public boolean call(ServerPlayerEntity caller, String destination) {
        if (!canUse(caller, true) || isBusy(caller.getUuid())) {
            if (isBusy(caller.getUuid())) {
                caller.sendMessage(Text.literal("§cDu bist bereits in einem Gespräch oder Anruf."));
            }
            return false;
        }
        Target target = resolveTarget(caller, destination);
        if (target == null) {
            caller.sendMessage(Text.literal("§cNummer oder Spieler nicht erreichbar."));
            return false;
        }
        ServerPlayerEntity callee = target.player();
        if (callee.getUuid().equals(caller.getUuid()) || isBusy(callee.getUuid()) || !canReceive(callee)) {
            caller.sendMessage(Text.literal("§cDer Anschluss ist besetzt oder nicht erreichbar."));
            return false;
        }
        PendingCall pending = new PendingCall(caller.getUuid(), callee.getUuid(), System.currentTimeMillis(), target.label());
        pendingByCaller.put(caller.getUuid(), pending);
        pendingByCallee.put(callee.getUuid(), pending);
        notices.remove(caller.getUuid());
        notices.remove(callee.getUuid());
        caller.sendMessage(Text.literal("§eAnruf an " + target.label() + " …"));
        callee.sendMessage(Text.literal("§aEingehender Anruf von " + caller.getGameProfile().getName()
                + " §7– /phone answer oder /phone decline"));
        return true;
    }

    public boolean answer(ServerPlayerEntity callee) {
        PendingCall pending = pendingByCallee.remove(callee.getUuid());
        if (pending == null) {
            callee.sendMessage(Text.literal("§cKein eingehender Anruf."));
            return false;
        }
        pendingByCaller.remove(pending.callerId());
        ServerPlayerEntity caller = server.getPlayerManager().getPlayer(pending.callerId());
        if (caller == null || !canUse(caller, false) || !canReceive(callee)) {
            callee.sendMessage(Text.literal("§cDer Anruf ist nicht mehr erreichbar."));
            if (caller != null) {
                caller.sendMessage(Text.literal("§cDer Anruf konnte nicht verbunden werden."));
            }
            return false;
        }
        CallSession session = new CallSession(caller.getUuid(), callee.getUuid(), pending.label());
        activeCalls.put(caller.getUuid(), session);
        activeCalls.put(callee.getUuid(), session);
        notices.remove(caller.getUuid());
        notices.remove(callee.getUuid());
        caller.sendMessage(Text.literal("§aVerbindung hergestellt: " + pending.label()));
        callee.sendMessage(Text.literal("§aVerbindung hergestellt: " + caller.getGameProfile().getName()));
        return true;
    }

    public boolean decline(ServerPlayerEntity callee) {
        PendingCall pending = pendingByCallee.remove(callee.getUuid());
        if (pending == null) {
            callee.sendMessage(Text.literal("§cKein eingehender Anruf."));
            return false;
        }
        pendingByCaller.remove(pending.callerId());
        ServerPlayerEntity caller = server.getPlayerManager().getPlayer(pending.callerId());
        if (caller != null) {
            caller.sendMessage(Text.literal("§cAnruf abgelehnt."));
            notice(caller.getUuid(), "Anruf abgelehnt");
        }
        notice(callee.getUuid(), "Anruf abgelehnt");
        callee.sendMessage(Text.literal("§7Anruf abgelehnt."));
        return true;
    }

    public boolean hangup(ServerPlayerEntity player) {
        PendingCall outgoing = pendingByCaller.remove(player.getUuid());
        if (outgoing != null) {
            pendingByCallee.remove(outgoing.calleeId());
            notifyPlayer(outgoing.calleeId(), "§7Der Anruf wurde beendet.");
            notice(outgoing.calleeId(), "Anruf beendet");
            notice(player.getUuid(), "Anruf beendet");
            player.sendMessage(Text.literal("§7Anruf beendet."));
            return true;
        }
        PendingCall incoming = pendingByCallee.remove(player.getUuid());
        if (incoming != null) {
            pendingByCaller.remove(incoming.callerId());
            notifyPlayer(incoming.callerId(), "§7Der Anruf wurde beendet.");
            notice(incoming.callerId(), "Anruf beendet");
            notice(player.getUuid(), "Anruf beendet");
            player.sendMessage(Text.literal("§7Anruf beendet."));
            return true;
        }
        CallSession session = activeCalls.remove(player.getUuid());
        if (session == null) {
            player.sendMessage(Text.literal("§cDu telefonierst gerade nicht."));
            return false;
        }
        UUID other = session.other(player.getUuid());
        activeCalls.remove(other);
        speakerEnabled.remove(player.getUuid());
        speakerEnabled.remove(other);
        notifyPlayer(other, "§7Die Gegenstelle hat aufgelegt.");
        notice(other, "Gespräch beendet");
        notice(player.getUuid(), "Gespräch beendet");
        player.sendMessage(Text.literal("§7Gespräch beendet."));
        return true;
    }

    public boolean toggleSpeaker(ServerPlayerEntity player) {
        if (!activeCalls.containsKey(player.getUuid())) {
            player.sendMessage(Text.literal("§cLautsprecher ist nur während eines Gesprächs verfügbar."));
            return false;
        }
        boolean enabled;
        if (speakerEnabled.remove(player.getUuid())) {
            enabled = false;
        } else {
            speakerEnabled.add(player.getUuid());
            enabled = true;
        }
        player.sendMessage(Text.literal(enabled ? "§aTelefonlautsprecher eingeschaltet." : "§7Telefonlautsprecher ausgeschaltet."));
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

    public void tick() {
        long timeout = config.ringTimeoutSeconds * 1_000L;
        long now = System.currentTimeMillis();
        pendingByCaller.values().stream()
                .filter(call -> now - call.createdAt() >= timeout)
                .toList()
                .forEach(call -> {
                    if (pendingByCaller.remove(call.callerId(), call)) {
                        pendingByCallee.remove(call.calleeId(), call);
                        notifyPlayer(call.callerId(), "§cKeine Antwort.");
                        notifyPlayer(call.calleeId(), "§7Verpasster Anruf.");
                        notice(call.callerId(), "Keine Antwort");
                        notice(call.calleeId(), "Verpasster Anruf");
                    }
                });
        activeCalls.values().stream().distinct().toList().forEach(call -> {
            ServerPlayerEntity first = server.getPlayerManager().getPlayer(call.first());
            ServerPlayerEntity second = server.getPlayerManager().getPlayer(call.second());
            if (first == null || second == null || !hasCoverage(first) || !hasCoverage(second)) {
                endSession(call, "§cVerbindung wegen fehlendem Mobilfunknetz getrennt.");
            }
        });
    }

    public void onDisconnect(UUID playerId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            hangup(player);
            return;
        }
        CallSession call = activeCalls.remove(playerId);
        if (call != null) {
            UUID other = call.other(playerId);
            activeCalls.remove(other);
            speakerEnabled.remove(playerId);
            speakerEnabled.remove(other);
            notifyPlayer(other, "§cVerbindung getrennt.");
            notice(other, "Verbindung getrennt");
        }
        PendingCall outgoing = pendingByCaller.remove(playerId);
        if (outgoing != null) {
            pendingByCallee.remove(outgoing.calleeId());
        }
        PendingCall incoming = pendingByCallee.remove(playerId);
        if (incoming != null) {
            pendingByCaller.remove(incoming.callerId());
        }
    }

    public String status(ServerPlayerEntity player) {
        PlayerProfiles.Profile profile = profile(player);
        CallSession call = activeCalls.get(player.getUuid());
        String connection = call == null ? "kein Gespräch" : "verbunden mit " + nameOf(call.other(player.getUuid()));
        return "§6Telefon §7| Nummer: §f" + profile.phoneNumber + " §7| " + connection
                + " §7| Netz: " + (hasCoverage(player) ? "§aJa" : "§cNein");
    }

    private Target resolveTarget(ServerPlayerEntity caller, String destination) {
        EmergencyConfig.Number emergency = emergencyConfig.enabled ? emergencyConfig.numbers.stream()
                .filter(number -> number.number.equals(destination))
                .findFirst()
                .orElse(null) : null;
        if (emergency != null) {
            ServerPlayerEntity responder = server.getPlayerManager().getPlayerList().stream()
                    .filter(player -> !player.getUuid().equals(caller.getUuid()))
                    .filter(player -> onTeam(player, emergency.responderTeam))
                    .filter(player -> !isBusy(player.getUuid()) && canReceive(player))
                    .min(Comparator.comparingDouble(player -> player.squaredDistanceTo(caller)))
                    .orElse(null);
            return responder == null ? null : new Target(responder, emergency.displayName + " (" + emergency.number + ")");
        }
        PlayerProfiles.Profile byNumber = profiles.findByNumber(destination);
        ServerPlayerEntity target = byNumber == null ? server.getPlayerManager().getPlayer(destination)
                : server.getPlayerManager().getPlayer(byNumber.playerId);
        return target == null ? null : new Target(target, target.getGameProfile().getName());
    }

    private boolean canUse(ServerPlayerEntity player, boolean report) {
        if (!config.enabled) {
            if (report) player.sendMessage(Text.literal("§cTelefone sind deaktiviert."));
            return false;
        }
        if (config.requirePhoneItem && !deviceItems.hasPhone(player)) {
            if (report) player.sendMessage(Text.literal("§cDu benötigst ein Mobiltelefon."));
            return false;
        }
        if (!hasCoverage(player)) {
            if (report) player.sendMessage(Text.literal("§cKein Mobilfunknetz."));
            return false;
        }
        return true;
    }

    private boolean canReceive(ServerPlayerEntity player) {
        return canUse(player, false);
    }

    private boolean isBusy(UUID playerId) {
        return activeCalls.containsKey(playerId) || pendingByCaller.containsKey(playerId) || pendingByCallee.containsKey(playerId);
    }

    private boolean onTeam(ServerPlayerEntity player, String team) {
        return team == null || team.isBlank()
                || (player.getScoreboardTeam() != null && player.getScoreboardTeam().getName().equalsIgnoreCase(team));
    }

    private void endSession(CallSession call, String message) {
        activeCalls.remove(call.first());
        activeCalls.remove(call.second());
        speakerEnabled.remove(call.first());
        speakerEnabled.remove(call.second());
        notifyPlayer(call.first(), message);
        notifyPlayer(call.second(), message);
        notice(call.first(), "Verbindung getrennt");
        notice(call.second(), "Verbindung getrennt");
    }

    private void notifyPlayer(UUID playerId, String message) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        if (player != null) {
            player.sendMessage(Text.literal(message));
        }
    }

    private String nameOf(UUID playerId) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
        return player == null ? "unbekannt" : player.getGameProfile().getName();
    }

    private record PendingCall(UUID callerId, UUID calleeId, long createdAt, String label) {
    }

    private record Target(ServerPlayerEntity player, String label) {
    }

    public record CallSession(UUID first, UUID second, String label) {
        public UUID other(UUID playerId) {
            if (first.equals(playerId)) {
                return second;
            }
            if (second.equals(playerId)) {
                return first;
            }
            throw new IllegalArgumentException("Spieler ist kein Teilnehmer dieses Gesprächs: " + playerId);
        }
    }

    public ClientView clientView(ServerPlayerEntity player) {
        UUID playerId = player.getUuid();
        PendingCall incoming = pendingByCallee.get(playerId);
        PendingCall outgoing = pendingByCaller.get(playerId);
        CallSession active = activeCalls.get(playerId);
        String state = active != null ? "active" : incoming != null ? "incoming" : outgoing != null ? "ringing" : "idle";
        String peer = "";
        if (active != null) {
            peer = nameOf(active.other(playerId));
        } else if (incoming != null) {
            peer = nameOf(incoming.callerId());
        } else if (outgoing != null) {
            peer = nameOf(outgoing.calleeId());
        }
        PlayerProfiles.Profile profile = profile(player);
        Notice notice = notices.get(playerId);
        String noticeText = notice != null && notice.expiresAt() > System.currentTimeMillis() ? notice.text() : "";
        if (notice != null && noticeText.isEmpty()) {
            notices.remove(playerId, notice);
        }
        return new ClientView(state, peer, profile.phoneNumber, isSpeakerEnabled(playerId), hasCoverage(player),
                noticeText, Map.copyOf(profile.contacts));
    }

    public record ClientView(String state, String peer, String number, boolean speaker,
                             boolean coverage, String notice, Map<String, String> contacts) {
    }

    private void notice(UUID playerId, String text) {
        notices.put(playerId, new Notice(text, System.currentTimeMillis() + noticeDurationMillis));
    }

    private record Notice(String text, long expiresAt) {
    }
}
