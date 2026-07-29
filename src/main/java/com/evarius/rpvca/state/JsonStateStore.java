package com.evarius.rpvca.state;

import com.evarius.rpvca.RpVoiceAddon;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Supplier;

public final class JsonStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path directory;

    public JsonStateStore(MinecraftServer server) {
        directory = server.getSavePath(WorldSavePath.ROOT).resolve("rp-voice-additions");
    }

    public <T> T load(String fileName, Class<T> type, Supplier<T> defaults) {
        Path file = directory.resolve(fileName);
        if (!Files.exists(file)) {
            return defaults.get();
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            T value = GSON.fromJson(reader, type);
            return value == null ? defaults.get() : value;
        } catch (IOException | RuntimeException exception) {
            try {
                Files.createDirectories(directory);
                Files.copy(file, file.resolveSibling(fileName + ".invalid"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException backupException) {
                exception.addSuppressed(backupException);
            }
            RpVoiceAddon.LOGGER.error("Statusdatei {} konnte nicht geladen werden", file, exception);
            return defaults.get();
        }
    }

    public synchronized void save(String fileName, Object value) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(fileName);
            Path temporary = directory.resolve(fileName + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary)) {
                GSON.toJson(value, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            RpVoiceAddon.LOGGER.error("Statusdatei {} konnte nicht gespeichert werden", fileName, exception);
        }
    }
}
