package com.evarius.rpvca.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Public API for TerraNexus and other optional control-center integrations. */
public interface SirenApi {
    List<SirenControllerView> controllers();

    SirenActionResult triggerScenario(ServerPlayer actor, UUID controllerId, String scenarioId);

    /** Trusted server-mod entry point for automatic systems without a player context. */
    SirenActionResult triggerScenario(UUID controllerId, String scenarioId, String sourceId);

    SirenActionResult stop(ServerPlayer actor, UUID controllerId);

    SirenActionResult schedule(ServerPlayer actor, UUID controllerId, String scenarioId, Instant executeAt);

    SirenActionResult cancelScheduled(ServerPlayer actor, UUID alarmId);

    SirenActionResult startLiveAnnouncement(ServerPlayer actor, UUID controllerId);

    SirenActionResult stopLiveAnnouncement(ServerPlayer actor);
}
