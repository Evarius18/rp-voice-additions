package com.evarius.rpvca.state;

import com.evarius.rpvca.config.InfrastructureConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
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
    }

    public synchronized void add(RegistryKey<World> world, BlockPos pos) {
        Tower tower = new Tower(world.getValue().toString(), pos.getX(), pos.getY(), pos.getZ(), true);
        data.towers.removeIf(existing -> existing.sameLocation(tower));
        data.towers.add(tower);
        save();
    }

    public synchronized void remove(RegistryKey<World> world, BlockPos pos) {
        Tower tower = new Tower(world.getValue().toString(), pos.getX(), pos.getY(), pos.getZ(), true);
        if (data.towers.removeIf(existing -> existing.sameLocation(tower))) {
            save();
        }
    }

    public synchronized boolean hasCoverage(ServerPlayerEntity player) {
        if (!config.enabled) {
            return true;
        }
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        double maximumSquared = config.towerRange * config.towerRange;
        return data.towers.stream().anyMatch(tower -> tower.active
                && (!config.requireSameDimension || tower.dimension.equals(dimension))
                && tower.squaredDistance(player.getX(), player.getY(), player.getZ()) <= maximumSquared);
    }

    public synchronized List<Tower> all() {
        return List.copyOf(data.towers);
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

        public Tower() {
        }

        public Tower(String dimension, int x, int y, int z, boolean active) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.active = active;
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
}
