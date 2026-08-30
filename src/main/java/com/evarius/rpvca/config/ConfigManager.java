package com.evarius.rpvca.config;

import com.evarius.rpvca.RpVoiceAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DIRECTORY = FabricLoader.getInstance().getConfigDir().resolve("rp-voice-additions");

    private volatile SpeechConfig speech;
    private volatile PhoneConfig phone;
    private volatile EmergencyConfig emergency;
    private volatile RadioConfig radio;
    private volatile InfrastructureConfig infrastructure;
    private volatile DeviceConfig devices;
    private volatile HudConfig hud;
    private volatile CompatibilityConfig compatibility;
    private volatile SirenConfig siren;

    public void load() {
        try {
            Files.createDirectories(DIRECTORY);
            speech = read("speech.json", SpeechConfig.class, SpeechConfig::new);
            phone = read("phone.json", PhoneConfig.class, PhoneConfig::new);
            emergency = read("emergency.json", EmergencyConfig.class, EmergencyConfig::new);
            radio = read("radio.json", RadioConfig.class, RadioConfig::new);
            infrastructure = read("infrastructure.json", InfrastructureConfig.class, InfrastructureConfig::new);
            devices = read("devices.json", DeviceConfig.class, DeviceConfig::new);
            hud = read("hud.json", HudConfig.class, HudConfig::new);
            compatibility = read("compatibility.json", CompatibilityConfig.class, CompatibilityConfig::new);
            siren = read("siren.json", SirenConfig.class, SirenConfig::new);
            validate();
            saveAll();
        } catch (IOException exception) {
            throw new IllegalStateException("RP Voice Additions Konfiguration konnte nicht geladen werden", exception);
        }
    }

    private <T> T read(String fileName, Class<T> type, Supplier<T> defaults) throws IOException {
        Path file = DIRECTORY.resolve(fileName);
        if (!Files.exists(file)) {
            return defaults.get();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            T value = GSON.fromJson(reader, type);
            return value == null ? defaults.get() : value;
        } catch (RuntimeException exception) {
            Path invalid = file.resolveSibling(fileName + ".invalid");
            Files.copy(file, invalid, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            RpVoiceAddon.LOGGER.error("Ungültige Config {}. Eine Sicherung wurde als {} angelegt.", file, invalid, exception);
            return defaults.get();
        }
    }

    private void validate() {
        if (speech.modes == null || speech.modes.isEmpty()) {
            speech = new SpeechConfig();
        }
        Set<String> modeIds = new HashSet<>();
        speech.modes.removeIf(mode -> mode == null || mode.id == null || mode.id.isBlank()
                || !mode.id.matches("[a-zA-Z0-9_-]+")
                || !Float.isFinite(mode.distance) || mode.distance <= 0
                || !modeIds.add(mode.id.toLowerCase(Locale.ROOT)));
        if (speech.modes.isEmpty()) {
            speech = new SpeechConfig();
        }
        if (speech.defaultMode == null || speech.modes.stream().noneMatch(mode -> mode.id.equalsIgnoreCase(speech.defaultMode))) {
            speech.defaultMode = speech.modes.getFirst().id;
        }
        if (speech.modeOrder == null) {
            speech.modeOrder = new SpeechConfig().modeOrder;
        }
        Set<String> validSpeechIds = speech.modes.stream()
                .map(mode -> mode.id.toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        validSpeechIds.add("WHISPER");
        speech.modeOrder = speech.modeOrder.stream().filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .filter(validSpeechIds::contains).distinct().toList();
        if (speech.modeOrder.isEmpty()) {
            speech.modeOrder = new java.util.ArrayList<>(new SpeechConfig().modeOrder);
        } else {
            speech.modeOrder = new java.util.ArrayList<>(speech.modeOrder);
        }
        phone.numberLength = Math.clamp(phone.numberLength, 3, 12);
        phone.ringTimeoutSeconds = Math.clamp(phone.ringTimeoutSeconds, 5, 120);
        phone.maxContactNameLength = Math.clamp(phone.maxContactNameLength, 1, 64);
        phone.maxContactsPerPlayer = Math.clamp(phone.maxContactsPerPlayer, 1, 1_000);
        phone.maxCallHistoryEntries = Math.clamp(phone.maxCallHistoryEntries, 0, 1_000);
        phone.historyAdminPermissionLevel = Math.clamp(phone.historyAdminPermissionLevel, 0, 4);
        if (phone.phoneNumberGeneration == null) {
            phone.phoneNumberGeneration = new PhoneConfig.PhoneNumberGeneration();
        }
        PhoneConfig.PhoneNumberGeneration numberGeneration = phone.phoneNumberGeneration;
        if (numberGeneration.prefix == null || !numberGeneration.prefix.matches("[0-9]*")) {
            RpVoiceAddon.LOGGER.warn("Ungültige Telefonnummern-Vorwahl; Standardwert wird verwendet");
            numberGeneration.prefix = new PhoneConfig.PhoneNumberGeneration().prefix;
        }
        numberGeneration.randomDigits = Math.clamp(numberGeneration.randomDigits, 1, 12);
        if (numberGeneration.separator == null || numberGeneration.separator.length() > 4
                || !numberGeneration.separator.matches("[\\s.\\-/]*")) {
            numberGeneration.separator = new PhoneConfig.PhoneNumberGeneration().separator;
        }
        numberGeneration.maxGenerationAttempts = Math.clamp(numberGeneration.maxGenerationAttempts, 1, 100_000);
        if (!Float.isFinite(phone.speakerDistance)) {
            phone.speakerDistance = new PhoneConfig().speakerDistance;
        }
        phone.speakerDistance = Math.max(1.0F, phone.speakerDistance);
        if (emergency.numbers == null) {
            emergency.numbers = new EmergencyConfig().numbers;
        }
        Set<String> emergencyNumbers = new HashSet<>();
        emergency.numbers.removeIf(number -> number == null || number.number == null
                || !number.number.matches("[0-9*#]+") || !emergencyNumbers.add(number.number));
        emergency.numbers.forEach(number -> {
            if (number.displayName == null || number.displayName.isBlank()) {
                number.displayName = number.number;
            }
            if (number.responderTeam == null) {
                number.responderTeam = "";
            }
            if (number.responderKeys == null) {
                number.responderKeys = new java.util.ArrayList<>();
            }
            if (!number.responderTeam.isBlank() && number.responderKeys.isEmpty()) {
                number.responderKeys.add(number.responderTeam);
            }
            number.responderKeys = number.responderKeys.stream().filter(java.util.Objects::nonNull)
                    .map(value -> value.trim().toLowerCase(Locale.ROOT))
                    .filter(value -> value.matches("[a-z0-9_.-]+")).distinct().toList();
        });
        if (radio.channels == null) {
            radio.channels = new RadioConfig().channels;
        }
        Set<String> channelIds = new HashSet<>();
        radio.channels.removeIf(channel -> channel == null || channel.id == null || channel.id.isBlank()
                || !channel.id.matches("[a-zA-Z0-9_.-]+")
                || !channelIds.add(channel.id.toLowerCase(Locale.ROOT)));
        radio.channels.forEach(channel -> {
            if (channel.displayName == null || channel.displayName.isBlank()) {
                channel.displayName = channel.id;
            }
            if (channel.requiredTeam == null) {
                channel.requiredTeam = "";
            }
        });
        if (!Double.isFinite(radio.maximumRange)) {
            radio.maximumRange = new RadioConfig().maximumRange;
        }
        radio.deviceLocation = switch (radio.deviceLocation == null ? "" : radio.deviceLocation.toUpperCase(Locale.ROOT)) {
            case "HAND", "HOTBAR" -> radio.deviceLocation.toUpperCase(Locale.ROOT);
            default -> "INVENTORY";
        };
        if (!Double.isFinite(infrastructure.towerRange)) {
            infrastructure.towerRange = new InfrastructureConfig().towerRange;
        }
        infrastructure.towerRange = Math.max(1.0D, infrastructure.towerRange);
        if (!Double.isFinite(infrastructure.digitalRadioRelayRange)) {
            infrastructure.digitalRadioRelayRange = new InfrastructureConfig().digitalRadioRelayRange;
        }
        infrastructure.digitalRadioRelayRange = Math.max(1.0D, infrastructure.digitalRadioRelayRange);
        if (devices.phoneItems == null || devices.phoneItems.isEmpty()) {
            devices.phoneItems = new DeviceConfig().phoneItems;
        }
        if (devices.radioItems == null || devices.radioItems.isEmpty()) {
            devices.radioItems = new DeviceConfig().radioItems;
        }
        if (devices.externalPhoneItems == null) {
            devices.externalPhoneItems = new DeviceConfig().externalPhoneItems;
        }
        devices.phoneItems = cleanIdentifiers(devices.phoneItems, new DeviceConfig().phoneItems);
        devices.externalPhoneItems = cleanIdentifiers(devices.externalPhoneItems, java.util.List.of());
        devices.radioItems = cleanIdentifiers(devices.radioItems, new DeviceConfig().radioItems);
        hud.horizontalAnchor = "left".equalsIgnoreCase(hud.horizontalAnchor) ? "left" : "right";
        hud.offsetX = Math.clamp(hud.offsetX, 0, 500);
        hud.offsetY = Math.clamp(hud.offsetY, 0, 500);
        hud.notificationDurationSeconds = Math.clamp(hud.notificationDurationSeconds, 1, 30);
        if (compatibility.institutionRadioChannels == null) {
            compatibility.institutionRadioChannels = new CompatibilityConfig().institutionRadioChannels;
        }
        compatibility.institutionRadioChannels.entrySet().removeIf(entry -> entry.getKey() == null
                || entry.getKey().isBlank() || entry.getValue() == null);
        compatibility.institutionRadioChannels.replaceAll((key, channels) ->
                channels.stream().filter(java.util.Objects::nonNull).map(String::trim)
                        .filter(value -> value.matches("[a-zA-Z0-9_.-]+")).distinct().toList());
        if (siren.configVersion < 2) {
            // Migrate the original effectively global 512-block default to realistic attenuation.
            if (Math.abs(siren.audibleDistance - 512.0F) < 0.01F) siren.audibleDistance = 192.0F;
            siren.configVersion = 2;
        }
        siren.audibleDistance = Float.isFinite(siren.audibleDistance)
                ? Math.clamp(siren.audibleDistance, 16.0F, 4096.0F) : new SirenConfig().audibleDistance;
        siren.signalGain = Float.isFinite(siren.signalGain)
                ? Math.clamp(siren.signalGain, 0.01F, 1.0F) : new SirenConfig().signalGain;
        siren.announcementGain = Float.isFinite(siren.announcementGain)
                ? Math.clamp(siren.announcementGain, 0.01F, 1.0F) : new SirenConfig().announcementGain;
        siren.maxLinkedSirensPerController = Math.clamp(siren.maxLinkedSirensPerController, 1, 512);
        siren.operatePermissionLevel = Math.clamp(siren.operatePermissionLevel, 0, 4);
        siren.configurePermissionLevel = Math.clamp(siren.configurePermissionLevel, 0, 4);
        siren.maximumScheduledAlarmsPerController = Math.clamp(
                siren.maximumScheduledAlarmsPerController, 1, 1_000);
        siren.maximumAnnouncementSeconds = Math.clamp(siren.maximumAnnouncementSeconds, 5, 600);
        siren.maximumSavedAnnouncementsPerController = Math.clamp(
                siren.maximumSavedAnnouncementsPerController, 1, 100);
        siren.operatorMembershipKeys = cleanPermissionKeys(siren.operatorMembershipKeys,
                new SirenConfig().operatorMembershipKeys);
        siren.configurationMembershipKeys = cleanPermissionKeys(siren.configurationMembershipKeys,
                new SirenConfig().configurationMembershipKeys);
        if (siren.scenarios == null) {
            siren.scenarios = new SirenConfig().scenarios;
        }
        Set<String> scenarioIds = new HashSet<>();
        Set<String> signals = Set.of("fire_alarm", "warning", "all_clear", "test");
        siren.scenarios.removeIf(scenario -> scenario == null || scenario.id == null
                || !scenario.id.matches("[a-z0-9_.-]{1,32}")
                || !scenarioIds.add(scenario.id.toLowerCase(Locale.ROOT)));
        siren.scenarios.forEach(scenario -> {
            if (scenario.displayName == null || scenario.displayName.isBlank()) {
                scenario.displayName = scenario.id;
            }
            if (scenario.steps == null) {
                scenario.steps = new java.util.ArrayList<>();
            }
            scenario.steps.removeIf(step -> step == null || step.signal == null
                    || !signals.contains(step.signal.toLowerCase(Locale.ROOT)));
            scenario.steps.forEach(step -> {
                step.signal = step.signal.toLowerCase(Locale.ROOT);
                step.delaySeconds = Math.clamp(step.delaySeconds, 0, 86_400);
            });
        });
        siren.scenarios.removeIf(scenario -> scenario.steps.isEmpty());
        if (siren.scenarios.isEmpty()) {
            siren.scenarios = new SirenConfig().scenarios;
        }
    }

    private void saveAll() throws IOException {
        write("speech.json", speech);
        write("phone.json", phone);
        write("emergency.json", emergency);
        write("radio.json", radio);
        write("infrastructure.json", infrastructure);
        write("devices.json", devices);
        write("hud.json", hud);
        write("compatibility.json", compatibility);
        write("siren.json", siren);
    }

    private void write(String fileName, Object value) throws IOException {
        try (Writer writer = Files.newBufferedWriter(DIRECTORY.resolve(fileName))) {
            GSON.toJson(value, writer);
        }
    }

    public SpeechConfig speech() {
        return speech;
    }

    public PhoneConfig phone() {
        return phone;
    }

    public EmergencyConfig emergency() {
        return emergency;
    }

    public RadioConfig radio() {
        return radio;
    }

    public InfrastructureConfig infrastructure() {
        return infrastructure;
    }

    public DeviceConfig devices() {
        return devices;
    }

    public HudConfig hud() {
        return hud;
    }

    public CompatibilityConfig compatibility() {
        return compatibility;
    }

    public SirenConfig siren() {
        return siren;
    }

    private static java.util.List<String> cleanIdentifiers(java.util.List<String> values,
                                                            java.util.List<String> fallback) {
        values.stream().filter(java.util.Objects::nonNull).map(String::trim)
                .filter(value -> !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                .forEach(value -> RpVoiceAddon.LOGGER.warn("Ungültige konfigurierte Item-ID '{}' wird ignoriert", value));
        java.util.List<String> cleaned = values.stream().filter(java.util.Objects::nonNull)
                .map(String::trim).filter(value -> value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                .distinct().toList();
        return cleaned.isEmpty() ? new java.util.ArrayList<>(fallback) : new java.util.ArrayList<>(cleaned);
    }

    private static java.util.List<String> cleanPermissionKeys(java.util.List<String> values,
                                                               java.util.List<String> fallback) {
        if (values == null) return new java.util.ArrayList<>(fallback);
        return new java.util.ArrayList<>(values.stream().filter(java.util.Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> value.matches("[a-z0-9_.-]{1,64}"))
                .distinct().toList());
    }
}
