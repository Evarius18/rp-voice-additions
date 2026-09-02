package com.evarius.rpvca.siren;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.api.SirenActionResult;
import com.evarius.rpvca.api.SirenApi;
import com.evarius.rpvca.api.SirenControllerView;
import com.evarius.rpvca.config.SirenConfig;
import com.evarius.rpvca.content.ModContent;
import com.evarius.rpvca.network.CommunicationNetworking;
import com.evarius.rpvca.permissions.VanillaPermissionLevels;
import com.evarius.rpvca.state.JsonStateStore;
import com.evarius.rpvca.voice.SirenVoiceEngine;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent, server-authoritative network of controllers, sirens, schedules and recordings. */
public final class SirenService implements SirenApi {
    private static final DateTimeFormatter CLOCK_TIME = DateTimeFormatter.ofPattern("H:mm");

    private final MinecraftServer server;
    private final SirenConfig config;
    private final JsonStateStore store;
    private final Path announcementDirectory;
    private final Map<UUID, OpenSession> openSessions = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingLinks = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveScenario> activeScenarios = new ConcurrentHashMap<>();
    private Data data;

    public SirenService(MinecraftServer server, SirenConfig config, JsonStateStore store) {
        this.server = server;
        this.config = config;
        this.store = store;
        this.announcementDirectory = server.getSavePath(WorldSavePath.ROOT)
                .resolve("rp-voice-additions").resolve("announcements");
        data = store.load("sirens.json", Data.class, Data::new);
        normalizeState();
        reconcile();
    }

    public synchronized UUID registerSiren(RegistryKey<World> world, BlockPos pos, String variant) {
        SirenNode existing = sirenAt(world, pos);
        if (existing != null) {
            existing.variant = variant;
            save();
            return existing.id;
        }
        SirenNode node = new SirenNode(UUID.randomUUID(), world.getValue().toString(), pos, variant);
        data.sirens.add(node);
        save();
        return node.id;
    }

    public synchronized void removeSiren(RegistryKey<World> world, BlockPos pos) {
        Set<UUID> removed = new LinkedHashSet<>();
        data.sirens.removeIf(node -> {
            boolean matches = node.samePosition(world, pos);
            if (matches) removed.add(node.id);
            return matches;
        });
        if (!removed.isEmpty()) {
            data.controllers.forEach(controller -> controller.linkedSirens.removeAll(removed));
            save();
        }
    }

    public synchronized UUID registerController(RegistryKey<World> world, BlockPos pos) {
        Controller existing = controllerAt(world, pos);
        if (existing != null) {
            return existing.id;
        }
        Controller controller = new Controller(UUID.randomUUID(), world.getValue().toString(), pos,
                "block.rp-vca.siren_controller");
        data.controllers.add(controller);
        save();
        return controller.id;
    }

    public synchronized void removeController(RegistryKey<World> world, BlockPos pos) {
        Controller controller = controllerAt(world, pos);
        if (controller == null) {
            return;
        }
        stopInternal(controller.id);
        data.controllers.remove(controller);
        data.scheduled.removeIf(alarm -> alarm.controllerId.equals(controller.id));
        data.announcements.removeIf(announcement -> announcement.controllerId.equals(controller.id));
        openSessions.entrySet().removeIf(entry -> entry.getValue().controllerId.equals(controller.id));
        pendingLinks.entrySet().removeIf(entry -> entry.getValue().equals(controller.id));
        save();
    }

    public synchronized boolean openController(ServerPlayerEntity player, RegistryKey<World> world, BlockPos pos) {
        if (!config.enabled) {
            player.sendMessage(Text.translatable("message.rp-vca.siren.disabled"), true);
            return false;
        }
        Controller controller = controllerAt(world, pos);
        if (controller == null || player.squaredDistanceTo(pos.getX() + 0.5D,
                pos.getY() + 0.5D, pos.getZ() + 0.5D) > 64.0D) {
            return false;
        }
        openSessions.put(player.getUuid(), new OpenSession(controller.id, System.currentTimeMillis()));
        sendSnapshot(player, "");
        return true;
    }

    public synchronized SirenActionResult beginLinkMode(ServerPlayerEntity player) {
        Controller controller = controllerForSession(player);
        if (controller == null) return SirenActionResult.INVALID_REQUEST;
        if (!canConfigure(player)) return SirenActionResult.NOT_ALLOWED;
        if (!isHoldingProgrammer(player)) return SirenActionResult.PROGRAMMER_REQUIRED;
        pendingLinks.put(player.getUuid(), controller.id);
        player.sendMessage(Text.translatable("message.rp-vca.siren.link_mode"), true);
        return SirenActionResult.SUCCESS;
    }

    public synchronized SirenActionResult completeLink(ServerPlayerEntity player,
                                                        RegistryKey<World> world, BlockPos pos) {
        UUID controllerId = pendingLinks.remove(player.getUuid());
        if (controllerId == null) return SirenActionResult.INVALID_REQUEST;
        if (!isHoldingProgrammer(player)) {
            player.sendMessage(Text.translatable("message.rp-vca.siren.programmer_required"), true);
            return SirenActionResult.PROGRAMMER_REQUIRED;
        }
        Controller controller = controller(controllerId);
        SirenNode siren = sirenAt(world, pos);
        ServerWorld playerWorld = player.getEntityWorld();
        if (siren == null && playerWorld.getRegistryKey().equals(world)) {
            Block block = playerWorld.getBlockState(pos).getBlock();
            if (block == ModContent.MAST_SIRENE_ZWEI || block == ModContent.MAST_SIRENE_DREI) {
                registerSiren(world, pos, block == ModContent.MAST_SIRENE_DREI ? "three_head" : "two_head");
                siren = sirenAt(world, pos);
            }
        }
        if (controller == null || siren == null) return SirenActionResult.NOT_FOUND;
        if (!canConfigure(player)) return SirenActionResult.NOT_ALLOWED;
        if (!config.allowCrossDimensionLinks && !controller.dimension.equals(siren.dimension)) {
            player.sendMessage(Text.translatable("message.rp-vca.siren.cross_dimension_denied"), true);
            return SirenActionResult.INVALID_REQUEST;
        }
        if (controller.linkedSirens.remove(siren.id)) {
            save();
            player.sendMessage(Text.translatable("message.rp-vca.siren.unlinked"), true);
            return SirenActionResult.SUCCESS;
        }
        if (controller.linkedSirens.size() >= config.maxLinkedSirensPerController) {
            return SirenActionResult.LIMIT_REACHED;
        }
        controller.linkedSirens.add(siren.id);
        save();
        player.sendMessage(Text.translatable("message.rp-vca.siren.linked"), true);
        return SirenActionResult.SUCCESS;
    }

    public boolean hasPendingLink(UUID playerId) {
        return pendingLinks.containsKey(playerId);
    }

    public synchronized SirenActionResult triggerFromOpenSession(ServerPlayerEntity player, String scenarioId) {
        Controller controller = controllerForSession(player);
        return controller == null ? SirenActionResult.INVALID_REQUEST
                : triggerScenario(player, controller.id, scenarioId);
    }

    @Override
    public synchronized SirenActionResult triggerScenario(ServerPlayerEntity actor,
                                                           UUID controllerId, String scenarioId) {
        if (!canOperate(actor)) return SirenActionResult.NOT_ALLOWED;
        return triggerInternal(controllerId, scenarioId, actor.getUuidAsString());
    }

    @Override
    public synchronized SirenActionResult triggerScenario(UUID controllerId,
                                                           String scenarioId, String sourceId) {
        if (sourceId == null || sourceId.isBlank() || sourceId.length() > 64) {
            return SirenActionResult.INVALID_REQUEST;
        }
        return triggerInternal(controllerId, scenarioId, sourceId);
    }

    private SirenActionResult triggerInternal(UUID controllerId, String scenarioId, String sourceId) {
        if (!config.enabled) return SirenActionResult.DISABLED;
        Controller controller = controller(controllerId);
        SirenConfig.Scenario scenario = scenario(scenarioId);
        if (controller == null || scenario == null) return SirenActionResult.NOT_FOUND;
        if (resolveEmitters(controller).isEmpty()) return SirenActionResult.NO_LINKED_SIRENS;
        if (!SirenVoiceEngine.available()) return SirenActionResult.VOICE_CHAT_UNAVAILABLE;
        for (SirenConfig.Step step : scenario.steps) {
            SirenSignal signal = SirenSignal.fromId(step.signal);
            if (signal == null || !SirenVoiceEngine.prepareSignal(signal)) {
                return SirenActionResult.AUDIO_UNAVAILABLE;
            }
        }
        stopInternal(controllerId);
        activeScenarios.put(controllerId, new ActiveScenario(controllerId, scenario,
                0, System.currentTimeMillis() + scenario.steps.getFirst().delaySeconds * 1_000L));
        RpVoiceAddon.LOGGER.info("Sirenen-Szenario '{}' an Steuerung {} durch {} ausgelöst",
                scenario.id, controllerId, sourceId);
        tickActiveScenarios(System.currentTimeMillis());
        try {
            ServerPlayerEntity operator = server.getPlayerManager().getPlayer(UUID.fromString(sourceId));
            if (operator != null && !SirenVoiceEngine.isPlayerListening(operator.getUuid())) {
                operator.sendMessage(Text.translatable("message.rp-vca.siren.voice_chat_disabled"), true);
            }
        } catch (IllegalArgumentException ignored) {
            // Scheduled alarms and trusted integrations use descriptive source identifiers.
        }
        return SirenActionResult.SUCCESS;
    }

    @Override
    public synchronized SirenActionResult stop(ServerPlayerEntity actor, UUID controllerId) {
        if (!canOperate(actor)) return SirenActionResult.NOT_ALLOWED;
        if (controller(controllerId) == null) return SirenActionResult.NOT_FOUND;
        stopInternal(controllerId);
        return SirenActionResult.SUCCESS;
    }

    public synchronized SirenActionResult stopFromOpenSession(ServerPlayerEntity player) {
        Controller controller = controllerForSession(player);
        return controller == null ? SirenActionResult.INVALID_REQUEST : stop(player, controller.id);
    }

    private void stopInternal(UUID controllerId) {
        activeScenarios.remove(controllerId);
        SirenVoiceEngine.stopController(controllerId);
    }

    @Override
    public synchronized SirenActionResult schedule(ServerPlayerEntity actor, UUID controllerId,
                                                    String scenarioId, Instant executeAt) {
        if (!canConfigure(actor)) return SirenActionResult.NOT_ALLOWED;
        return scheduleInternal(controllerId, scenarioId, executeAt, actor.getUuidAsString());
    }

    public synchronized SirenActionResult scheduleFromOpenSession(ServerPlayerEntity actor,
                                                                   String scenarioId, String time) {
        Controller controller = controllerForSession(actor);
        if (controller == null) return SirenActionResult.INVALID_REQUEST;
        Optional<Instant> instant = parseScheduleTime(time);
        return instant.isEmpty() ? SirenActionResult.INVALID_REQUEST
                : schedule(actor, controller.id, scenarioId, instant.get());
    }

    private SirenActionResult scheduleInternal(UUID controllerId, String scenarioId,
                                                Instant executeAt, String sourceId) {
        Controller controller = controller(controllerId);
        if (controller == null || scenario(scenarioId) == null || executeAt == null
                || !executeAt.isAfter(Instant.now())
                || executeAt.isAfter(Instant.now().plus(Duration.ofDays(366)))) {
            return SirenActionResult.INVALID_REQUEST;
        }
        long count = data.scheduled.stream().filter(alarm -> alarm.controllerId.equals(controllerId)).count();
        if (count >= config.maximumScheduledAlarmsPerController) return SirenActionResult.LIMIT_REACHED;
        data.scheduled.add(new ScheduledAlarm(UUID.randomUUID(), controllerId, scenarioId,
                executeAt.toEpochMilli(), sourceId));
        save();
        return SirenActionResult.SUCCESS;
    }

    @Override
    public synchronized SirenActionResult cancelScheduled(ServerPlayerEntity actor, UUID alarmId) {
        if (!canConfigure(actor)) return SirenActionResult.NOT_ALLOWED;
        boolean removed = data.scheduled.removeIf(alarm -> alarm.id.equals(alarmId));
        if (removed) save();
        return removed ? SirenActionResult.SUCCESS : SirenActionResult.NOT_FOUND;
    }

    public synchronized SirenActionResult cancelFromOpenSession(ServerPlayerEntity actor, UUID alarmId) {
        Controller controller = controllerForSession(actor);
        if (controller == null || data.scheduled.stream().noneMatch(alarm ->
                alarm.id.equals(alarmId) && alarm.controllerId.equals(controller.id))) {
            return SirenActionResult.NOT_FOUND;
        }
        return cancelScheduled(actor, alarmId);
    }

    @Override
    public synchronized SirenActionResult startLiveAnnouncement(ServerPlayerEntity actor, UUID controllerId) {
        if (!canConfigure(actor)) return SirenActionResult.NOT_ALLOWED;
        Controller controller = controller(controllerId);
        if (controller == null) return SirenActionResult.NOT_FOUND;
        List<SirenEmitter> emitters = resolveEmitters(controller);
        if (emitters.isEmpty()) return SirenActionResult.NO_LINKED_SIRENS;
        if (!SirenVoiceEngine.available()) return SirenActionResult.VOICE_CHAT_UNAVAILABLE;
        if (!SirenVoiceEngine.startLive(actor.getUuid(), controller.id, emitters, config.audibleDistance)) {
            return SirenActionResult.ALREADY_ACTIVE;
        }
        return SirenActionResult.SUCCESS;
    }

    public synchronized SirenActionResult startLiveFromOpenSession(ServerPlayerEntity actor) {
        Controller controller = controllerForSession(actor);
        return controller == null ? SirenActionResult.INVALID_REQUEST
                : startLiveAnnouncement(actor, controller.id);
    }

    @Override
    public synchronized SirenActionResult stopLiveAnnouncement(ServerPlayerEntity actor) {
        return SirenVoiceEngine.stopLive(actor.getUuid())
                ? SirenActionResult.SUCCESS : SirenActionResult.NOT_FOUND;
    }

    public synchronized SirenActionResult startRecording(ServerPlayerEntity actor) {
        Controller controller = controllerForSession(actor);
        if (controller == null) return SirenActionResult.INVALID_REQUEST;
        if (!canConfigure(actor)) return SirenActionResult.NOT_ALLOWED;
        long count = data.announcements.stream()
                .filter(value -> value.controllerId.equals(controller.id)).count();
        if (count >= config.maximumSavedAnnouncementsPerController) return SirenActionResult.LIMIT_REACHED;
        if (!SirenVoiceEngine.available()) return SirenActionResult.VOICE_CHAT_UNAVAILABLE;
        return SirenVoiceEngine.startRecording(actor.getUuid(), config.maximumAnnouncementSeconds)
                ? SirenActionResult.SUCCESS : SirenActionResult.ALREADY_ACTIVE;
    }

    public synchronized SirenActionResult stopAndSaveRecording(ServerPlayerEntity actor, String requestedName) {
        Controller controller = controllerForSession(actor);
        if (controller == null || !canConfigure(actor)) return SirenActionResult.NOT_ALLOWED;
        String name = normalizeAnnouncementName(requestedName);
        if (name == null) return SirenActionResult.INVALID_REQUEST;
        Optional<SirenVoiceEngine.RecordedAudio> recording = SirenVoiceEngine.stopRecording(actor.getUuid());
        if (recording.isEmpty() || recording.get().pcm().length == 0) return SirenActionResult.NOT_FOUND;
        UUID id = UUID.randomUUID();
        Path target = announcementDirectory.resolve(id + ".pcm");
        Path temporary = announcementDirectory.resolve(id + ".pcm.tmp");
        try {
            Files.createDirectories(announcementDirectory);
            Files.write(temporary, recording.get().pcm());
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            data.announcements.add(new Announcement(id, controller.id, name,
                    id + ".pcm", System.currentTimeMillis(), recording.get().durationMillis()));
            save();
            return SirenActionResult.SUCCESS;
        } catch (IOException exception) {
            RpVoiceAddon.LOGGER.error("Sirenen-Durchsage konnte nicht gespeichert werden", exception);
            return SirenActionResult.STORAGE_ERROR;
        }
    }

    public synchronized SirenActionResult playAnnouncement(ServerPlayerEntity actor, UUID announcementId) {
        Controller controller = controllerForSession(actor);
        if (controller == null || !canOperate(actor)) return SirenActionResult.NOT_ALLOWED;
        Announcement announcement = data.announcements.stream().filter(value -> value.id.equals(announcementId)
                && value.controllerId.equals(controller.id)).findFirst().orElse(null);
        if (announcement == null) return SirenActionResult.NOT_FOUND;
        List<SirenEmitter> emitters = resolveEmitters(controller);
        if (emitters.isEmpty()) return SirenActionResult.NO_LINKED_SIRENS;
        try {
            byte[] pcm = Files.readAllBytes(announcementDirectory.resolve(announcement.fileName));
            return SirenVoiceEngine.playRecorded(controller.id, pcm, emitters,
                    config.audibleDistance, config.announcementGain)
                    ? SirenActionResult.SUCCESS : SirenActionResult.VOICE_CHAT_UNAVAILABLE;
        } catch (IOException exception) {
            RpVoiceAddon.LOGGER.error("Gespeicherte Durchsage {} konnte nicht gelesen werden", announcement.id,
                    exception);
            return SirenActionResult.STORAGE_ERROR;
        }
    }

    public synchronized SirenActionResult removeAnnouncement(ServerPlayerEntity actor, UUID announcementId) {
        Controller controller = controllerForSession(actor);
        if (controller == null || !canConfigure(actor)) return SirenActionResult.NOT_ALLOWED;
        Announcement announcement = data.announcements.stream().filter(value -> value.id.equals(announcementId)
                && value.controllerId.equals(controller.id)).findFirst().orElse(null);
        if (announcement == null) return SirenActionResult.NOT_FOUND;
        data.announcements.remove(announcement);
        try {
            Files.deleteIfExists(announcementDirectory.resolve(announcement.fileName));
        } catch (IOException exception) {
            RpVoiceAddon.LOGGER.warn("Audiodatei der Durchsage {} konnte nicht gelöscht werden", announcement.id,
                    exception);
        }
        save();
        return SirenActionResult.SUCCESS;
    }

    public synchronized void tick() {
        long now = System.currentTimeMillis();
        tickActiveScenarios(now);
        List<ScheduledAlarm> due = data.scheduled.stream().filter(alarm -> alarm.executeAt <= now).toList();
        if (!due.isEmpty()) {
            data.scheduled.removeAll(due);
            save();
            due.forEach(alarm -> triggerInternal(alarm.controllerId, alarm.scenarioId,
                    "schedule:" + alarm.sourceId));
        }
        openSessions.entrySet().removeIf(entry -> now - entry.getValue().openedAt > 30L * 60_000L);
        pendingLinks.entrySet().removeIf(entry -> !openSessions.containsKey(entry.getKey()));
    }

    private void tickActiveScenarios(long now) {
        for (ActiveScenario active : List.copyOf(activeScenarios.values())) {
            if (active.nextAt > now) continue;
            Controller controller = controller(active.controllerId);
            if (controller == null || active.stepIndex >= active.scenario.steps.size()) {
                activeScenarios.remove(active.controllerId);
                continue;
            }
            SirenConfig.Step step = active.scenario.steps.get(active.stepIndex);
            SirenSignal signal = SirenSignal.fromId(step.signal);
            List<SirenEmitter> emitters = resolveEmitters(controller);
            if (signal != null && !emitters.isEmpty()) {
                SirenVoiceEngine.playSignal(controller.id, signal, emitters,
                        config.audibleDistance, config.signalGain);
            }
            int nextIndex = active.stepIndex + 1;
            if (nextIndex >= active.scenario.steps.size()) {
                activeScenarios.remove(active.controllerId);
            } else {
                long nextAt = now + active.scenario.steps.get(nextIndex).delaySeconds * 1_000L;
                activeScenarios.put(active.controllerId,
                        new ActiveScenario(active.controllerId, active.scenario, nextIndex, nextAt));
            }
        }
    }

    public synchronized void onDisconnect(UUID playerId) {
        openSessions.remove(playerId);
        pendingLinks.remove(playerId);
        SirenVoiceEngine.stopLive(playerId);
        SirenVoiceEngine.stopRecording(playerId);
    }

    public synchronized void shutdown() {
        data.controllers.forEach(controller -> SirenVoiceEngine.stopController(controller.id));
        openSessions.keySet().forEach(SirenVoiceEngine::stopLive);
        openSessions.keySet().forEach(SirenVoiceEngine::stopRecording);
        openSessions.clear();
        pendingLinks.clear();
        activeScenarios.clear();
    }

    @Override
    public synchronized List<SirenControllerView> controllers() {
        return data.controllers.stream().map(controller -> new SirenControllerView(
                controller.id, controller.name, controller.dimension,
                controller.x, controller.y, controller.z,
                controller.linkedSirens.size(),
                (int) data.scheduled.stream().filter(alarm -> alarm.controllerId.equals(controller.id)).count(),
                SirenVoiceEngine.isControllerActive(controller.id)
        )).toList();
    }

    public synchronized void sendSnapshot(ServerPlayerEntity player, String noticeKey) {
        Controller controller = controllerForSession(player);
        if (controller == null) return;
        SirenControllerSnapshot snapshot = new SirenControllerSnapshot();
        snapshot.controllerId = controller.id.toString();
        snapshot.name = controller.name;
        snapshot.linkedSirens = controller.linkedSirens.size();
        snapshot.active = SirenVoiceEngine.isControllerActive(controller.id)
                || activeScenarios.containsKey(controller.id);
        snapshot.live = SirenVoiceEngine.isLive(player.getUuid());
        snapshot.recording = SirenVoiceEngine.isRecording(player.getUuid());
        snapshot.notice = noticeKey == null ? "" : noticeKey;
        config.scenarios.forEach(scenario -> snapshot.scenarios.add(
                new SirenControllerSnapshot.NamedOption(scenario.id, scenario.displayName)));
        data.scheduled.stream().filter(alarm -> alarm.controllerId.equals(controller.id))
                .sorted(Comparator.comparingLong(alarm -> alarm.executeAt))
                .forEach(alarm -> snapshot.scheduled.add(new SirenControllerSnapshot.ScheduledOption(
                        alarm.id.toString(), alarm.scenarioId, alarm.executeAt)));
        data.announcements.stream().filter(value -> value.controllerId.equals(controller.id))
                .sorted(Comparator.comparingLong((Announcement value) -> value.createdAt).reversed())
                .forEach(value -> snapshot.announcements.add(
                        new SirenControllerSnapshot.NamedOption(value.id.toString(), value.name)));
        CommunicationNetworking.openSirenController(player, snapshot);
    }

    private Optional<Instant> parseScheduleTime(String value) {
        if (value == null) return Optional.empty();
        String normalized = value.trim();
        try {
            if (normalized.matches("\\+?\\d{1,4}")) {
                int minutes = Integer.parseInt(normalized.replace("+", ""));
                return minutes > 0 ? Optional.of(Instant.now().plus(Duration.ofMinutes(minutes)))
                        : Optional.empty();
            }
            LocalTime time = LocalTime.parse(normalized, CLOCK_TIME);
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime scheduled = ZonedDateTime.of(LocalDate.now(zone), time, zone);
            if (!scheduled.toInstant().isAfter(Instant.now())) scheduled = scheduled.plusDays(1);
            return Optional.of(scheduled.toInstant());
        } catch (DateTimeException | NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private String normalizeAnnouncementName(String name) {
        if (name == null) return null;
        String normalized = name.trim().replaceAll("[\\p{Cntrl}]", "");
        return normalized.isEmpty() || normalized.length() > 48 ? null : normalized;
    }

    private boolean canOperate(ServerPlayerEntity player) {
        return hasPermission(player, config.operatePermissionLevel, config.operatorMembershipKeys);
    }

    private boolean isHoldingProgrammer(ServerPlayerEntity player) {
        return player.getMainHandStack().isOf(ModContent.SIREN_PROGRAMMER)
                || player.getOffHandStack().isOf(ModContent.SIREN_PROGRAMMER);
    }

    private boolean canConfigure(ServerPlayerEntity player) {
        return hasPermission(player, config.configurePermissionLevel, config.configurationMembershipKeys);
    }

    private boolean hasPermission(ServerPlayerEntity player, int operatorLevel, List<String> allowedKeys) {
        if (player == null) return false;
        if (VanillaPermissionLevels.has(player, operatorLevel)) return true;
        Set<String> memberships = new LinkedHashSet<>();
        if (player.getScoreboardTeam() != null) {
            memberships.add(player.getScoreboardTeam().getName().trim().toLowerCase(Locale.ROOT));
        }
        com.evarius.rpvca.api.RpVcaApi.institutionMembershipKeys(player).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT)).forEach(memberships::add);
        return allowedKeys.stream().anyMatch(memberships::contains);
    }

    private Controller controllerForSession(ServerPlayerEntity player) {
        OpenSession session = openSessions.get(player.getUuid());
        return session == null ? null : controller(session.controllerId);
    }

    private Controller controller(UUID id) {
        return data.controllers.stream().filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }

    private Controller controllerAt(RegistryKey<World> world, BlockPos pos) {
        return data.controllers.stream().filter(value -> value.samePosition(world, pos)).findFirst().orElse(null);
    }

    private SirenNode sirenAt(RegistryKey<World> world, BlockPos pos) {
        return data.sirens.stream().filter(value -> value.samePosition(world, pos)).findFirst().orElse(null);
    }

    private SirenConfig.Scenario scenario(String id) {
        if (id == null) return null;
        return config.scenarios.stream().filter(value -> value.id.equalsIgnoreCase(id.trim()))
                .findFirst().orElse(null);
    }

    private List<SirenEmitter> resolveEmitters(Controller controller) {
        List<SirenEmitter> result = new ArrayList<>();
        Set<UUID> ids = controller.linkedSirens;
        for (SirenNode node : data.sirens) {
            if (!ids.contains(node.id)) continue;
            Identifier dimensionId = Identifier.tryParse(node.dimension);
            if (dimensionId == null) continue;
            ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimensionId));
            if (world != null) result.add(new SirenEmitter(node.id, world, node.pos()));
        }
        return result;
    }

    private void normalizeState() {
        if (data.sirens == null) data.sirens = new ArrayList<>();
        if (data.controllers == null) data.controllers = new ArrayList<>();
        if (data.scheduled == null) data.scheduled = new ArrayList<>();
        if (data.announcements == null) data.announcements = new ArrayList<>();
        data.sirens.removeIf(value -> value == null || value.id == null || value.dimension == null);
        data.controllers.removeIf(value -> value == null || value.id == null || value.dimension == null);
        data.controllers.forEach(value -> {
            if (value.linkedSirens == null) value.linkedSirens = new LinkedHashSet<>();
            if (value.name == null || value.name.isBlank()) value.name = "block.rp-vca.siren_controller";
        });
        Set<UUID> controllerIds = data.controllers.stream().map(value -> value.id)
                .collect(java.util.stream.Collectors.toSet());
        data.scheduled.removeIf(value -> value == null || value.id == null
                || !controllerIds.contains(value.controllerId));
        data.announcements.removeIf(value -> value == null || value.id == null
                || !controllerIds.contains(value.controllerId) || value.fileName == null);
    }

    private void reconcile() {
        boolean changed = data.sirens.removeIf(node -> !matchesBlock(node.dimension, node.pos(),
                ModContent.MAST_SIRENE_ZWEI, ModContent.MAST_SIRENE_DREI));
        changed |= data.controllers.removeIf(controller -> !matchesBlock(controller.dimension,
                controller.pos(), ModContent.SIREN_CONTROLLER));
        Set<UUID> sirenIds = data.sirens.stream().map(value -> value.id)
                .collect(java.util.stream.Collectors.toSet());
        for (Controller controller : data.controllers) {
            changed |= controller.linkedSirens.removeIf(id -> !sirenIds.contains(id));
        }
        Set<UUID> controllerIds = data.controllers.stream().map(value -> value.id)
                .collect(java.util.stream.Collectors.toSet());
        changed |= data.scheduled.removeIf(value -> !controllerIds.contains(value.controllerId));
        if (changed) save();
    }

    private boolean matchesBlock(String dimension, BlockPos pos, Block... expected) {
        Identifier id = Identifier.tryParse(dimension);
        if (id == null) return false;
        ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, id));
        if (world == null) return false;
        Block block = world.getBlockState(pos).getBlock();
        for (Block candidate : expected) if (block == candidate) return true;
        return false;
    }

    private void save() {
        if (!store.save("sirens.json", data)) {
            RpVoiceAddon.LOGGER.error("Sirenenstatus konnte nicht gespeichert werden");
        }
    }

    public static final class Data {
        public List<SirenNode> sirens = new ArrayList<>();
        public List<Controller> controllers = new ArrayList<>();
        public List<ScheduledAlarm> scheduled = new ArrayList<>();
        public List<Announcement> announcements = new ArrayList<>();
    }

    public static final class SirenNode extends Positioned {
        public String variant;

        public SirenNode() {
        }

        private SirenNode(UUID id, String dimension, BlockPos pos, String variant) {
            super(id, dimension, pos);
            this.variant = variant;
        }
    }

    public static final class Controller extends Positioned {
        public String name;
        public Set<UUID> linkedSirens = new LinkedHashSet<>();

        public Controller() {
        }

        private Controller(UUID id, String dimension, BlockPos pos, String name) {
            super(id, dimension, pos);
            this.name = name;
        }
    }

    public abstract static class Positioned {
        public UUID id;
        public String dimension;
        public int x;
        public int y;
        public int z;

        protected Positioned() {
        }

        protected Positioned(UUID id, String dimension, BlockPos pos) {
            this.id = id;
            this.dimension = dimension;
            this.x = pos.getX();
            this.y = pos.getY();
            this.z = pos.getZ();
        }

        public BlockPos pos() {
            return new BlockPos(x, y, z);
        }

        public boolean samePosition(RegistryKey<World> world, BlockPos pos) {
            return dimension.equals(world.getValue().toString()) && x == pos.getX() && y == pos.getY() && z == pos.getZ();
        }
    }

    public static final class ScheduledAlarm {
        public UUID id;
        public UUID controllerId;
        public String scenarioId;
        public long executeAt;
        public String sourceId;

        public ScheduledAlarm() {
        }

        private ScheduledAlarm(UUID id, UUID controllerId, String scenarioId, long executeAt, String sourceId) {
            this.id = id;
            this.controllerId = controllerId;
            this.scenarioId = scenarioId;
            this.executeAt = executeAt;
            this.sourceId = sourceId;
        }
    }

    public static final class Announcement {
        public UUID id;
        public UUID controllerId;
        public String name;
        public String fileName;
        public long createdAt;
        public long durationMillis;

        public Announcement() {
        }

        private Announcement(UUID id, UUID controllerId, String name, String fileName,
                             long createdAt, long durationMillis) {
            this.id = id;
            this.controllerId = controllerId;
            this.name = name;
            this.fileName = fileName;
            this.createdAt = createdAt;
            this.durationMillis = durationMillis;
        }
    }

    private record OpenSession(UUID controllerId, long openedAt) {
    }

    private record ActiveScenario(UUID controllerId, SirenConfig.Scenario scenario,
                                  int stepIndex, long nextAt) {
    }
}
