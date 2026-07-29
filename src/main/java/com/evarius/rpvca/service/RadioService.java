package com.evarius.rpvca.service;

import com.evarius.rpvca.config.RadioConfig;
import com.evarius.rpvca.item.DeviceItemResolver;
import com.evarius.rpvca.permissions.RadioPermissionResolver;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RadioService {
    private final RadioConfig config;
    private final DeviceItemResolver deviceItems;
    private final RadioPermissionResolver permissions;
    private final Map<UUID, String> tunedChannels = new ConcurrentHashMap<>();
    private final Set<UUID> transmitting = ConcurrentHashMap.newKeySet();

    public RadioService(RadioConfig config, DeviceItemResolver deviceItems, RadioPermissionResolver permissions) {
        this.config = config;
        this.deviceItems = deviceItems;
        this.permissions = permissions;
    }

    public boolean tune(ServerPlayerEntity player, String id) {
        if (!config.enabled) {
            player.sendMessage(Text.literal("§cFunk ist deaktiviert."));
            return false;
        }
        if (config.requireRadioItem && !hasRadio(player)) {
            player.sendMessage(Text.literal("§cDu benötigst ein Funkgerät."));
            return false;
        }
        RadioConfig.Channel channel = channel(id);
        if (channel == null) {
            player.sendMessage(Text.literal("§cUnbekannter Funkkanal."));
            return false;
        }
        if (!hasAccess(player, channel)) {
            player.sendMessage(Text.literal("§cKeine Berechtigung für diesen Funkkanal."));
            return false;
        }
        tunedChannels.put(player.getUuid(), channel.id);
        transmitting.remove(player.getUuid());
        player.sendMessage(Text.literal("§aFunkkanal: " + channel.displayName + " (" + channel.id + ")"));
        return true;
    }

    public void off(ServerPlayerEntity player) {
        tunedChannels.remove(player.getUuid());
        transmitting.remove(player.getUuid());
        player.sendMessage(Text.literal("§7Funkgerät ausgeschaltet."));
    }

    public boolean toggleTransmit(ServerPlayerEntity player) {
        if (!canOperate(player)) {
            player.sendMessage(Text.literal("§cWähle zuerst einen verfügbaren Funkkanal."));
            return false;
        }
        boolean enabled;
        if (transmitting.remove(player.getUuid())) {
            enabled = false;
        } else {
            transmitting.add(player.getUuid());
            enabled = true;
        }
        player.sendMessage(Text.literal(enabled ? "§cFunkübertragung aktiv (TX)." : "§7Funkübertragung beendet."));
        return enabled;
    }

    public boolean setTransmitting(ServerPlayerEntity player, boolean enabled) {
        if (enabled && !canOperate(player)) {
            transmitting.remove(player.getUuid());
            return false;
        }
        if (enabled) {
            transmitting.add(player.getUuid());
        } else {
            transmitting.remove(player.getUuid());
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

    public boolean canTransmit(ServerPlayerEntity player) {
        return transmitting.contains(player.getUuid()) && canOperate(player);
    }

    private boolean canOperate(ServerPlayerEntity player) {
        String id = tunedChannel(player.getUuid());
        RadioConfig.Channel channel = id == null ? null : channel(id);
        return config.enabled && channel != null && hasAccess(player, channel)
                && (!config.requireRadioItem || hasRadio(player));
    }

    public boolean canReceive(ServerPlayerEntity sender, ServerPlayerEntity receiver) {
        String channelId = tunedChannel(sender.getUuid());
        if (channelId == null || !channelId.equalsIgnoreCase(tunedChannel(receiver.getUuid()))
                || !canOperate(receiver) || sender.getUuid().equals(receiver.getUuid())) {
            return false;
        }
        if (config.requireSameDimension && sender.getWorld() != receiver.getWorld()) {
            return false;
        }
        return config.maximumRange <= 0.0D
                || (sender.getWorld() == receiver.getWorld()
                && sender.squaredDistanceTo(receiver) <= config.maximumRange * config.maximumRange);
    }

    public String status(ServerPlayerEntity player) {
        String id = tunedChannel(player.getUuid());
        RadioConfig.Channel channel = id == null ? null : channel(id);
        return channel == null ? "§7Funkgerät: aus"
                : "§6Funkgerät §7| Kanal: §f" + channel.displayName + " (" + channel.id + ") §7| "
                + (transmitting.contains(player.getUuid()) ? "§cTX" : "§aRX");
    }

    public void onDisconnect(UUID playerId) {
        tunedChannels.remove(playerId);
        transmitting.remove(playerId);
    }

    private boolean hasRadio(ServerPlayerEntity player) {
        return deviceItems.hasRadio(player);
    }

    private boolean hasAccess(ServerPlayerEntity player, RadioConfig.Channel channel) {
        return permissions.hasAccess(player, channel);
    }

    public java.util.List<RadioConfig.Channel> accessibleChannels(ServerPlayerEntity player) {
        return config.channels.stream().filter(channel -> hasAccess(player, channel)).toList();
    }
}
