package com.evarius.rpvca.phone;

import com.evarius.rpvca.api.PhoneNumberAllocationResult;
import com.evarius.rpvca.config.PhoneConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneNumberServiceTest {
    @Test
    void normalizesFormatsAndGeneratesUniqueNumbers() {
        PhoneConfig config = new PhoneConfig();
        config.phoneNumberGeneration.prefix = "555";
        config.phoneNumberGeneration.randomDigits = 4;
        config.phoneNumberGeneration.separator = "-";
        PhoneNumberService service = new PhoneNumberService(config, Set.of("112"), new Random(7));
        Set<String> allocated = new HashSet<>();

        PhoneNumberAllocationResult first = service.generate(allocated);
        assertEquals(PhoneNumberAllocationResult.Status.CREATED, first.status());
        assertTrue(first.normalizedNumber().matches("555[0-9]{4}"));
        assertEquals("555-" + first.normalizedNumber().substring(3), first.formattedNumber());
        allocated.add(first.normalizedNumber());

        PhoneNumberAllocationResult second = service.generate(allocated);
        assertNotEquals(first.normalizedNumber(), second.normalizedNumber());
        assertEquals("0151483920", service.normalize("0151 483-920").orElseThrow());
    }

    @Test
    void exhaustedSpaceFailsWithinConfiguredAttempts() {
        PhoneConfig config = new PhoneConfig();
        config.phoneNumberGeneration.prefix = "9";
        config.phoneNumberGeneration.randomDigits = 1;
        config.phoneNumberGeneration.maxGenerationAttempts = 20;
        PhoneNumberService service = new PhoneNumberService(config, Set.of(), new Random(1));
        Set<String> allocated = new HashSet<>();
        for (int digit = 0; digit <= 9; digit++) {
            allocated.add("9" + digit);
        }

        assertEquals(PhoneNumberAllocationResult.Status.NUMBER_SPACE_EXHAUSTED,
                service.generate(allocated).status());
    }
}
