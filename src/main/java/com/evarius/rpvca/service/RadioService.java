package com.evarius.rpvca.service;

import com.evarius.rpvca.config.RadioConfig;
import com.evarius.rpvca.item.DeviceItemResolver;
import com.evarius.rpvca.permissions.RadioPermissionResolver;
import com.evarius.rpvca.state.TowerRegistry;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class RadioService {
    private final RadioConfig config;
    private final DeviceItemResolver deviceItems;
    private final RadioPermissionResolver permissions;
    private final TowerRegistry towers;
    private final Map<UUID, String> tunedChannels = new ConcurrentHashMap<>();
    private final Set<UUID> transmitting = ConcurrentHashMap.newKeySet();

    public RadioService(RadioConfig config, DeviceItemResolver deviceItems, RadioPermissionResolver permissions,
                        TowerRegistry towers) {
        this.config = config;
        this.deviceItems = deviceItems;
        this.permissions = permissions;
        this.towers = towers;
    }

    public boolean tune(ServerPlayer player, String id) {
        if (!config.enabled) {
            player.sendSystemMessage(Component.translatable("message.rp-vca.radio.disabled"));
            return false;
        }
        if (config.requireRadioItem && !hasRadio(player)) {
            player.sendSystemMessage(Component.translatable("message.rp-vca.radio.device_required"));
            return false;
        }
        RadioConfig.Channel channel = channel(id);
        if (channel == null) {
            player.sendSystemMessage(Component.translatable("message.rp-vca.radio.unknown_channel"));
            return false;
        }
        if (!hasAccess(player, channel)) {
            player.sendSystemMessage(Component.translatable("message.rp-vca.radio.not_allowed"));
            return false;
        }
        tunedChannels.put(player.getUUID(), channel.id);
        transmitting.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable("message.rp-vca.radio.tuned", channel.displayName, channel.id));
        return true;
    }

    public void off(ServerPlayer player) {
        tunedChannels.remove(player.getUUID());
        transmitting.remove(player.getUUID());
        player.sendSystemMessage(Component.translatable("message.rp-vca.radio.off"));
    }

    public boolean toggleTransmit(ServerPlayer player) {
        if (!canOperate(player)) {
            player.sendSystemMessage(Component.translatable("message.rp-vca.radio.select_channel"));
            return false;
        }
        boolean enabled;
        if (transmitting.remove(player.getUUID())) {
            enabled = false;
        } else {
            transmitting.add(player.getUUID());
            enabled = true;
        }
        player.sendSystemMessage(Component.translatable(enabled
                ? "message.rp-vca.radio.transmitting" : "message.rp-vca.radio.transmit_stopped"));
        return enabled;
    }

    public boolean setTransmitting(ServerPlayer player, boolean enabled) {
        if (enabled && !canOperate(player)) {
            transmitting.remove(player.getUUID());
            return false;
        }
        if (enabled) {
            transmitting.add(player.getUUID());
        } else {
            transmitting.remove(player.getUUID());
        }
        return true;
    }

    public boolean isTransmitting(UUID playerId) {
        return transmitting.contains(playerId);
    }

    public String tunedChannel(UUID playerId) {
        return tunedChannels.get(playerId);
    }

    public RadioConfig.Channel channel(String id) {
        return config.channels.stream().filter(channel -> channel.id.equalsIgnoreCase(id)).findFirst().orElse(null);
    }

    public boolean canTransmit(ServerPlayer player) {
        return transmitting.contains(player.getUUID()) && canOperate(player);
    }

    private boolean canOperate(ServerPlayer player) {
        String id = tunedChannel(player.getUUID());
        RadioConfig.Channel channel = id == null ? null : channel(id);
        return config.enabled && channel != null && hasAccess(player, channel)
                && (!config.requireRadioItem || hasRadio(player));
    }

    public boolean canReceive(ServerPlayer sender, ServerPlayer receiver) {
        String channelId = tunedChannel(sender.getUUID());
        if (channelId == null || !channelId.equalsIgnoreCase(tunedChannel(receiver.getUUID()))
                || !canOperate(receiver) || sender.getUUID().equals(receiver.getUUID())) {
            return false;
        }
        if (config.requireSameDimension && sender.level() != receiver.level()) {
            return false;
        }
        if (config.maximumRange <= 0.0D) {
            return true;
        }
        if (sender.level() == receiver.level()
                && sender.distanceToSqr(receiver) <= config.maximumRange * config.maximumRange) {
            return true;
        }
        return towers.hasRadioRelayCoverage(sender) && towers.hasRadioRelayCoverage(receiver);
    }

    public String status(ServerPlayer player) {
        String id = tunedChannel(player.getUUID());
        RadioConfig.Channel channel = id == null ? null : channel(id);
        return channel == null ? "§7Funkgerät: aus"
                : "§6Funkgerät §7| Kanal: §f" + channel.displayName + " (" + channel.id + ") §7| "
                + (transmitting.contains(player.getUUID()) ? "§cTX" : "§aRX");
    }

    public void onDisconnect(UUID playerId) {
        tunedChannels.remove(playerId);
        transmitting.remove(playerId);
    }

    private boolean hasRadio(ServerPlayer player) {
        return deviceItems.hasRadio(player);
    }

    public boolean hasUsableRadio(ServerPlayer player) {
        return !config.requireRadioItem || hasRadio(player);
    }

    /**
     * Reports whether the player actually carries a configured radio device.
     * This deliberately ignores {@code requireRadioItem}: routing policy and
     * physical device presence are separate concerns.
     */
    public boolean hasRadioDevice(ServerPlayer player) {
        return hasRadio(player);
    }

    private boolean hasAccess(ServerPlayer player, RadioConfig.Channel channel) {
        return permissions.hasAccess(player, channel);
    }

    public java.util.List<RadioConfig.Channel> accessibleChannels(ServerPlayer player) {
        return config.channels.stream().filter(channel -> hasAccess(player, channel)).toList();
    }
}
