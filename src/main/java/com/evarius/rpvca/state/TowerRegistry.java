package com.evarius.rpvca.state;

import com.evarius.rpvca.config.InfrastructureConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public final class TowerRegistry {
    private final JsonStateStore store;
    private final InfrastructureConfig config;
    private Data data;

    public TowerRegistry(JsonStateStore store, InfrastructureConfig config) {
        this.store = store;
        this.config = config;
        data = store.load("towers.json", Data.class, Data::new);
        if (data.towers == null) {
            data.towers = new ArrayList<>();
        }
        data.towers.removeIf(tower -> tower == null || tower.dimension == null || tower.dimension.isBlank());
        boolean migrated = false;
        for (Tower tower : data.towers) {
            String normalizedType = TowerType.fromId(tower.type).id();
            if (!normalizedType.equals(tower.type)) {
                tower.type = normalizedType;
                migrated = true;
            }
        }
        if (migrated) {
            save();
        }
    }

    public synchronized void add(RegistryKey<World> world, BlockPos pos) {
        add(world, pos, TowerType.CELLULAR);
    }

    public synchronized void add(RegistryKey<World> world, BlockPos pos, TowerType type) {
        Tower tower = new Tower(world.getValue().toString(), pos.getX(), pos.getY(), pos.getZ(), true, type.id());
        data.towers.removeIf(existing -> existing.sameLocation(tower));
        data.towers.add(tower);
        save();
    }

    public synchronized void remove(RegistryKey<World> world, BlockPos pos) {
        Tower tower = new Tower(world.getValue().toString(), pos.getX(), pos.getY(), pos.getZ(), true,
                TowerType.CELLULAR.id());
        if (data.towers.removeIf(existing -> existing.sameLocation(tower))) {
            save();
        }
    }

    public synchronized boolean hasCoverage(ServerPlayerEntity player) {
        if (!config.enabled) {
            return true;
        }
        return hasCoverage(player, TowerType.CELLULAR, config.towerRange);
    }

    public synchronized boolean hasRadioRelayCoverage(ServerPlayerEntity player) {
        if (!config.digitalRadioRelaysEnabled) {
            return false;
        }
        return hasCoverage(player, TowerType.DIGITAL_RADIO, config.digitalRadioRelayRange);
    }

    private boolean hasCoverage(ServerPlayerEntity player, TowerType type, double range) {
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        double maximumSquared = range * range;
        return data.towers.stream().anyMatch(tower -> tower.active && type.id().equals(tower.type)
                && (!config.requireSameDimension || tower.dimension.equals(dimension))
                && tower.squaredDistance(player.getX(), player.getY(), player.getZ()) <= maximumSquared);
    }

    public synchronized List<Tower> all() {
        return List.copyOf(data.towers);
    }

    public synchronized long count(TowerType type) {
        return data.towers.stream().filter(tower -> type.id().equals(tower.type)).count();
    }

    /** Removes stale entries, including positions left by the retired cell_tower block. */
    public synchronized void reconcile(MinecraftServer server, Block cellularBlock, Block digitalRadioBlock) {
        boolean changed = data.towers.removeIf(tower -> {
            Identifier dimensionId = Identifier.tryParse(tower.dimension);
            if (dimensionId == null) {
                return true;
            }
            net.minecraft.server.world.ServerWorld world = server.getWorld(
                    RegistryKey.of(RegistryKeys.WORLD, dimensionId));
            if (world == null) {
                return true;
            }
            Block expected = TowerType.DIGITAL_RADIO.id().equals(tower.type)
                    ? digitalRadioBlock : cellularBlock;
            return !world.getBlockState(new BlockPos(tower.x, tower.y, tower.z)).isOf(expected);
        });
        if (changed) {
            save();
        }
    }

    private void save() {
        store.save("towers.json", data);
    }

    public static final class Data {
        public List<Tower> towers = new ArrayList<>();
    }

    public static final class Tower {
        public String dimension;
        public int x;
        public int y;
        public int z;
        public boolean active;
        public String type;

        public Tower() {
        }

        public Tower(String dimension, int x, int y, int z, boolean active) {
            this(dimension, x, y, z, active, TowerType.CELLULAR.id());
        }

        public Tower(String dimension, int x, int y, int z, boolean active, String type) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.active = active;
            this.type = TowerType.fromId(type).id();
        }

        private boolean sameLocation(Tower other) {
            return dimension.equals(other.dimension) && x == other.x && y == other.y && z == other.z;
        }

        private double squaredDistance(double otherX, double otherY, double otherZ) {
            double dx = x + 0.5D - otherX;
            double dy = y + 0.5D - otherY;
            double dz = z + 0.5D - otherZ;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public enum TowerType {
        CELLULAR("cellular"),
        DIGITAL_RADIO("digital_radio");

        private final String id;

        TowerType(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static TowerType fromId(String id) {
            if (id != null) {
                for (TowerType type : values()) {
                    if (type.id.equalsIgnoreCase(id)) {
                        return type;
                    }
                }
            }
            // Old towers.json entries did not contain a type and represented cell towers.
            return CELLULAR;
        }
    }
}
