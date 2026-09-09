package com.evarius.rpvca.api;

import com.evarius.rpvca.phone.history.CallHistoryEntryView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Stable public phone contract for optional integrations such as TerraNexus. */
public interface PhoneApi {
    ContactMutationResult upsertContact(ServerPlayer player, String name, String number);

    ContactMutationResult removeContact(ServerPlayer player, String name);

    Map<String, String> getContacts(ServerPlayer player);

    List<CallHistoryEntryView> getCallHistory(ServerPlayer player);

    HistoryMutationResult removeHistoryEntry(ServerPlayer player, UUID entryId);

    HistoryMutationResult clearOwnCallHistory(ServerPlayer player);

    HistoryMutationResult clearCallHistory(ServerPlayer administrator, UUID targetPlayerId);

    HistoryMutationResult clearAllCallHistories(ServerPlayer administrator);

    PhoneNumberAllocationResult allocateNumber(ServerPlayer player);

    Optional<String> getAssignedNumber(UUID playerId);

    PhoneStatusView getStatus(ServerPlayer player);

    List<EmergencyNumberView> getEmergencyNumbers();

    boolean call(ServerPlayer caller, String destination);

    CallMutationResult startCall(ServerPlayer caller, String destination);

    CallMutationResult acceptCall(ServerPlayer callee, UUID callId);

    CallMutationResult declineCall(ServerPlayer callee, UUID callId);

    CallerDisplay resolveCallerDisplay(ServerPlayer viewer, String callerNumber);

    boolean answer(ServerPlayer callee);

    boolean decline(ServerPlayer callee);

    boolean hangup(ServerPlayer player);

    boolean toggleSpeaker(ServerPlayer player);
}
