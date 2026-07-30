package com.evarius.rpvca.api;

import com.evarius.rpvca.phone.history.CallHistoryEntryView;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Stable public phone contract for optional integrations such as TerraNexus. */
public interface PhoneApi {
    ContactMutationResult upsertContact(ServerPlayerEntity player, String name, String number);

    ContactMutationResult removeContact(ServerPlayerEntity player, String name);

    Map<String, String> getContacts(ServerPlayerEntity player);

    List<CallHistoryEntryView> getCallHistory(ServerPlayerEntity player);

    HistoryMutationResult removeHistoryEntry(ServerPlayerEntity player, UUID entryId);

    HistoryMutationResult clearOwnCallHistory(ServerPlayerEntity player);

    HistoryMutationResult clearCallHistory(ServerPlayerEntity administrator, UUID targetPlayerId);

    HistoryMutationResult clearAllCallHistories(ServerPlayerEntity administrator);

    PhoneNumberAllocationResult allocateNumber(ServerPlayerEntity player);

    Optional<String> getAssignedNumber(UUID playerId);

    PhoneStatusView getStatus(ServerPlayerEntity player);

    List<EmergencyNumberView> getEmergencyNumbers();

    boolean call(ServerPlayerEntity caller, String destination);

    CallMutationResult startCall(ServerPlayerEntity caller, String destination);

    CallMutationResult acceptCall(ServerPlayerEntity callee, UUID callId);

    CallMutationResult declineCall(ServerPlayerEntity callee, UUID callId);

    CallerDisplay resolveCallerDisplay(ServerPlayerEntity viewer, String callerNumber);

    boolean answer(ServerPlayerEntity callee);

    boolean decline(ServerPlayerEntity callee);

    boolean hangup(ServerPlayerEntity player);

    boolean toggleSpeaker(ServerPlayerEntity player);
}
