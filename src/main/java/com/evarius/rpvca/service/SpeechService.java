package com.evarius.rpvca.service;

import com.evarius.rpvca.config.SpeechConfig;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-validated speech selection with native SVC whisper observation. */
public final class SpeechService {
    public static final String WHISPER = "whisper";
    private static final SpeechConfig.Mode WHISPER_MODE =
            new SpeechConfig.Mode(WHISPER, 0.0F);

    private final SpeechConfig config;
    private final Map<UUID, String> modes = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastNonWhisperModes = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> nativeWhisperActive = ConcurrentHashMap.newKeySet();

    public SpeechService(SpeechConfig config) {
        this.config = config;
    }

    public SpeechConfig.Mode mode(UUID playerId) {
        String id = selectedModeId(playerId);
        if (WHISPER.equals(id)) {
            return WHISPER_MODE;
        }
        return configuredMode(id);
    }

    public String selectedModeId(UUID playerId) {
        return modes.getOrDefault(playerId, config.defaultMode.toLowerCase(Locale.ROOT));
    }

    public boolean isWhisper(UUID playerId) {
        return WHISPER.equals(selectedModeId(playerId));
    }

    public SpeechConfig.Mode setMode(UUID playerId, String modeId) {
        if (modeId == null) {
            return null;
        }
        String normalized = modeId.toLowerCase(Locale.ROOT);
        if (WHISPER.equals(normalized) && order().contains(WHISPER)) {
            String current = selectedModeId(playerId);
            if (!WHISPER.equals(current)) {
                lastNonWhisperModes.put(playerId, current);
            }
            modes.put(playerId, WHISPER);
            return WHISPER_MODE;
        }
        SpeechConfig.Mode selected = config.modes.stream()
                .filter(mode -> mode.id.equalsIgnoreCase(normalized)).findFirst().orElse(null);
        if (selected != null && order().contains(selected.id.toLowerCase(Locale.ROOT))) {
            String value = selected.id.toLowerCase(Locale.ROOT);
            modes.put(playerId, value);
            lastNonWhisperModes.put(playerId, value);
        }
        return selected;
    }

    public SpeechConfig.Mode cycle(UUID playerId) {
        List<String> order = order();
        String current = selectedModeId(playerId);
        int index = order.indexOf(current);
        String next = order.get((index < 0 ? 0 : index + 1) % order.size());
        return setMode(playerId, next);
    }

    /**
     * Called only with the whisper flag from an actual SVC microphone packet.
     * This cannot be forged into whisper audio by an RP-VCA client action.
     */
    public void observeNativeWhisper(UUID playerId, boolean whispering) {
        if (whispering && order().contains(WHISPER)) {
            nativeWhisperActive.add(playerId);
            String current = selectedModeId(playerId);
            if (!WHISPER.equals(current)) {
                lastNonWhisperModes.put(playerId, current);
                modes.put(playerId, WHISPER);
            }
        } else if (!whispering && nativeWhisperActive.remove(playerId)
                && WHISPER.equals(selectedModeId(playerId))) {
            String fallback = lastNonWhisperModes.getOrDefault(playerId,
                    config.defaultMode.toLowerCase(Locale.ROOT));
            modes.put(playerId, configuredMode(fallback).id.toLowerCase(Locale.ROOT));
        }
    }

    private List<String> order() {
        return config.modeOrder.stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    }

    private SpeechConfig.Mode configuredMode(String id) {
        return config.modes.stream().filter(mode -> mode.id.equalsIgnoreCase(id))
                .findFirst().orElse(config.modes.getFirst());
    }
}
