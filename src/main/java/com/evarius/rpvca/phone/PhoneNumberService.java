package com.evarius.rpvca.phone;

import com.evarius.rpvca.api.PhoneNumberAllocationResult;
import com.evarius.rpvca.config.PhoneConfig;

import java.security.SecureRandom;
import java.util.Optional;
import java.util.Set;
import java.util.random.RandomGenerator;

/** Central normalization, display formatting and bounded unique-number generation. */
public final class PhoneNumberService {
    private final PhoneConfig config;
    private final Set<String> reservedNumbers;
    private final RandomGenerator random;

    public PhoneNumberService(PhoneConfig config, Set<String> reservedNumbers) {
        this(config, reservedNumbers, new SecureRandom());
    }

    PhoneNumberService(PhoneConfig config, Set<String> reservedNumbers, RandomGenerator random) {
        this.config = config;
        this.reservedNumbers = Set.copyOf(reservedNumbers);
        this.random = random;
    }

    public Optional<String> normalize(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String normalized = input.trim().replaceAll("[\\s()\\-./]", "");
        if (normalized.isEmpty() || normalized.length() > 24 || !normalized.matches("[0-9*#]+")) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    public String format(String normalized) {
        PhoneConfig.PhoneNumberGeneration generation = config.phoneNumberGeneration;
        String prefix = normalizeDigits(generation.prefix);
        if (generation.enabled && !generation.separator.isEmpty() && normalized.startsWith(prefix)
                && normalized.length() > prefix.length()) {
            return prefix + generation.separator + normalized.substring(prefix.length());
        }
        return normalized;
    }

    public PhoneNumberAllocationResult generate(Set<String> allocatedNumbers) {
        PhoneConfig.PhoneNumberGeneration generation = config.phoneNumberGeneration;
        if (!validConfiguration()) {
            return new PhoneNumberAllocationResult(
                    PhoneNumberAllocationResult.Status.INVALID_CONFIGURATION, "", "");
        }
        String prefix = normalizeDigits(generation.prefix);
        long range = powerOfTen(generation.randomDigits);
        if (range <= 0L) {
            return new PhoneNumberAllocationResult(
                    PhoneNumberAllocationResult.Status.INVALID_CONFIGURATION, "", "");
        }
        for (int attempt = 0; attempt < generation.maxGenerationAttempts; attempt++) {
            long value = random.nextLong(range);
            if (!generation.allowLeadingZeroInRandomPart && value < range / 10L) {
                continue;
            }
            String randomPart = String.format("%0" + generation.randomDigits + "d", value);
            String candidate = prefix + randomPart;
            if (!reservedNumbers.contains(candidate) && !allocatedNumbers.contains(candidate)) {
                return new PhoneNumberAllocationResult(PhoneNumberAllocationResult.Status.CREATED,
                        candidate, format(candidate));
            }
        }
        return new PhoneNumberAllocationResult(
                PhoneNumberAllocationResult.Status.NUMBER_SPACE_EXHAUSTED, "", "");
    }

    public boolean validConfiguration() {
        PhoneConfig.PhoneNumberGeneration generation = config.phoneNumberGeneration;
        if (!generation.enabled) {
            return true;
        }
        return generation.prefix != null && generation.prefix.matches("[0-9]*")
                && generation.randomDigits >= 1 && generation.randomDigits <= 12
                && generation.separator != null && generation.separator.length() <= 4
                && generation.maxGenerationAttempts >= 1 && generation.maxGenerationAttempts <= 100_000;
    }

    private static String normalizeDigits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static long powerOfTen(int digits) {
        long value = 1L;
        for (int i = 0; i < digits; i++) {
            if (value > Long.MAX_VALUE / 10L) {
                return -1L;
            }
            value *= 10L;
        }
        return value;
    }
}
