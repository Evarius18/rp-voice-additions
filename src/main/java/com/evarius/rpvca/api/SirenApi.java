package com.evarius.rpvca.api;

import net.minecraft.server.network.ServerPlayerEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Public API for TerraNexus and other optional control-center integrations. */
public interface SirenApi {
    List<SirenControllerView> controllers();

    SirenActionResult triggerScenario(ServerPlayerEntity actor, UUID controllerId, String scenarioId);

    /** Trusted server-mod entry point for automatic systems without a player context. */
    SirenActionResult triggerScenario(UUID controllerId, String scenarioId, String sourceId);

    SirenActionResult stop(ServerPlayerEntity actor, UUID controllerId);

    SirenActionResult schedule(ServerPlayerEntity actor, UUID controllerId, String scenarioId, Instant executeAt);

    SirenActionResult cancelScheduled(ServerPlayerEntity actor, UUID alarmId);

    SirenActionResult startLiveAnnouncement(ServerPlayerEntity actor, UUID controllerId);

    SirenActionResult stopLiveAnnouncement(ServerPlayerEntity actor);
}
