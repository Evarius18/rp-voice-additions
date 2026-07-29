package com.evarius.rpvca.client;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.config.ClientConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists settings that intentionally belong to each client rather than the server. */
public final class ClientConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("rp-voice-additions").resolve("client.json");
    private static ClientConfig config = new ClientConfig();

    private ClientConfigStore() {
    }

    public static void load() {
        try {
            Files.createDirectories(FILE.getParent());
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    ClientConfig loaded = GSON.fromJson(reader, ClientConfig.class);
                    config = loaded == null ? new ClientConfig() : loaded;
                } catch (RuntimeException exception) {
                    RpVoiceAddon.LOGGER.warn("Client-Konfiguration ist ungültig; Standardwerte werden verwendet",
                            exception);
                    config = new ClientConfig();
                }
            }
            config.radioVolume = Math.clamp(config.radioVolume, 0.0D, 2.0D);
            save();
        } catch (IOException exception) {
            RpVoiceAddon.LOGGER.warn("Client-Konfiguration konnte nicht geladen werden", exception);
        }
    }

    public static ClientConfig get() {
        return config;
    }

    public static void setRadioVolume(double volume) {
        config.radioVolume = Math.clamp(volume, 0.0D, 2.0D);
        try {
            save();
        } catch (IOException exception) {
            RpVoiceAddon.LOGGER.warn("Client-Konfiguration konnte nicht gespeichert werden", exception);
        }
    }

    private static void save() throws IOException {
        try (Writer writer = Files.newBufferedWriter(FILE)) {
            GSON.toJson(config, writer);
        }
    }
}
