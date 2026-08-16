package com.evarius.rpvca;

import com.evarius.rpvca.config.ConfigManager;
import com.evarius.rpvca.compatibility.CompatibilityManager;
import com.evarius.rpvca.content.ModContent;
import com.evarius.rpvca.item.DeviceItemResolver;
import com.evarius.rpvca.permissions.RadioPermissionResolver;
import com.evarius.rpvca.service.PhoneService;
import com.evarius.rpvca.service.RadioService;
import com.evarius.rpvca.service.SpeechService;
import com.evarius.rpvca.state.JsonStateStore;
import com.evarius.rpvca.state.PlayerProfiles;
import com.evarius.rpvca.state.TowerRegistry;
import net.minecraft.server.MinecraftServer;

import java.util.stream.Collectors;

public final class RpVoiceServices {
    private static volatile RpVoiceServices instance;

    private final MinecraftServer server;
    private final ConfigManager configs;
    private final JsonStateStore stateStore;
    private final PlayerProfiles profiles;
    private final TowerRegistry towers;
    private final SpeechService speech;
    private final PhoneService phones;
    private final RadioService radios;
    private final DeviceItemResolver deviceItems;
    private final CompatibilityManager compatibility;

    private RpVoiceServices(MinecraftServer server, ConfigManager configs) {
        this.server = server;
        this.configs = configs;
        stateStore = new JsonStateStore(server);
        profiles = new PlayerProfiles(stateStore, configs.phone(), configs.emergency().numbers.stream()
                .map(number -> number.number)
                .collect(Collectors.toUnmodifiableSet()));
        towers = new TowerRegistry(stateStore, configs.infrastructure());
        towers.reconcile(server, ModContent.MAST_MOBILFUNK, ModContent.MAST_DIGITALFUNK);
        deviceItems = new DeviceItemResolver(configs.devices(), configs.radio());
        compatibility = new CompatibilityManager(configs.compatibility());
        RadioPermissionResolver radioPermissions = new RadioPermissionResolver(compatibility, configs.compatibility());
        speech = new SpeechService(configs.speech());
        phones = new PhoneService(server, configs.phone(), configs.emergency(), profiles, towers, deviceItems,
                configs.hud().notificationDurationSeconds);
        radios = new RadioService(configs.radio(), deviceItems, radioPermissions, towers);
    }

    public static void start(MinecraftServer server, ConfigManager configs) {
        instance = new RpVoiceServices(server, configs);
    }

    public static void stop() {
        instance = null;
    }

    public static RpVoiceServices get() {
        return instance;
    }

    public MinecraftServer server() {
        return server;
    }

    public ConfigManager configs() {
        return configs;
    }

    public TowerRegistry towers() {
        return towers;
    }

    public SpeechService speech() {
        return speech;
    }

    public PhoneService phones() {
        return phones;
    }

    public RadioService radios() {
        return radios;
    }

    public DeviceItemResolver deviceItems() {
        return deviceItems;
    }

    public CompatibilityManager compatibility() {
        return compatibility;
    }
}
