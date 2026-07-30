package com.evarius.rpvca.state;

import com.evarius.rpvca.api.ContactMutationResult;
import com.evarius.rpvca.api.HistoryMutationResult;
import com.evarius.rpvca.config.PhoneConfig;
import com.evarius.rpvca.phone.history.CallDirection;
import com.evarius.rpvca.phone.history.CallHistoryStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerProfilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void validatesCreatesUpdatesAndRemovesContacts() {
        PhoneConfig config = config();
        config.maxContactsPerPlayer = 2;
        PlayerProfiles profiles = new PlayerProfiles(new JsonStateStore(temporaryDirectory), config, Set.of("112"));
        UUID player = UUID.randomUUID();

        assertEquals(ContactMutationResult.INVALID_NAME,
                profiles.upsertContact(player, "Tester", "", "123"));
        assertEquals(ContactMutationResult.INVALID_NUMBER,
                profiles.upsertContact(player, "Tester", "Alice", "not-a-number"));
        assertEquals(ContactMutationResult.CREATED,
                profiles.upsertContact(player, "Tester", "Alice", "555 1000"));
        assertEquals(ContactMutationResult.UPDATED,
                profiles.upsertContact(player, "Tester", "alice", "555-1001"));
        assertEquals(ContactMutationResult.DUPLICATE_NUMBER,
                profiles.upsertContact(player, "Tester", "Bob", "5551001"));
        assertEquals(ContactMutationResult.CREATED,
                profiles.upsertContact(player, "Tester", "Bob", "5551002"));
        assertEquals(ContactMutationResult.LIMIT_REACHED,
                profiles.upsertContact(player, "Tester", "Charlie", "5551003"));
        assertEquals(ContactMutationResult.REMOVED, profiles.removeContact(player, "ALICE"));
    }

    @Test
    void historyPersistsCalculatesDurationTrimsAndCanBeCleared() {
        PhoneConfig config = config();
        config.maxCallHistoryEntries = 2;
        UUID player = UUID.randomUUID();
        PlayerProfiles profiles = new PlayerProfiles(new JsonStateStore(temporaryDirectory), config, Set.of());
        PlayerProfiles.Profile profile = profiles.getOrCreate(player, "Tester");
        long start = 1_000L;
        for (int index = 0; index < 3; index++) {
            assertTrue(profiles.appendHistory(List.of(new PlayerProfiles.HistoryDraft(
                    player, CallDirection.OUTGOING, CallHistoryStatus.COMPLETED,
                    profile.phoneNumber, "555100" + index, "Remote " + index,
                    start + index * 10_000L, start + index * 10_000L + 2_000L,
                    start + index * 10_000L + 7_000L))));
        }

        PlayerProfiles reloaded = new PlayerProfiles(new JsonStateStore(temporaryDirectory), config, Set.of());
        assertEquals(2, reloaded.history(player).size());
        assertEquals(5L, reloaded.history(player).getFirst().durationSeconds());
        assertEquals("Remote 2", reloaded.history(player).getFirst().remoteDisplayName());
        assertEquals(HistoryMutationResult.CLEARED, reloaded.clearHistory(player));
        assertTrue(reloaded.history(player).isEmpty());
    }

    private static PhoneConfig config() {
        PhoneConfig config = new PhoneConfig();
        config.phoneNumberGeneration.prefix = "555";
        config.phoneNumberGeneration.randomDigits = 6;
        config.phoneNumberGeneration.separator = "-";
        return config;
    }
}
