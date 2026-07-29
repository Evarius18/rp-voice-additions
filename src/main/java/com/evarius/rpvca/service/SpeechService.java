package com.evarius.rpvca.service;

import com.evarius.rpvca.config.SpeechConfig;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SpeechService {
    private final SpeechConfig config;
    private final Map<UUID, String> modes = new ConcurrentHashMap<>();

    public SpeechService(SpeechConfig config) {
        this.config = config;
    }

    public SpeechConfig.Mode mode(UUID playerId) {
        String id = modes.getOrDefault(playerId, config.defaultMode);
        return config.modes.stream()
                .filter(mode -> mode.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(config.modes.getFirst());
    }

    public SpeechConfig.Mode setMode(UUID playerId, String modeId) {
        SpeechConfig.Mode selected = config.modes.stream()
                .filter(mode -> mode.id.equalsIgnoreCase(modeId))
                .findFirst()
                .orElse(null);
        if (selected != null) {
            modes.put(playerId, selected.id.toLowerCase(Locale.ROOT));
        }
        return selected;
    }

    public SpeechConfig.Mode cycle(UUID playerId) {
        SpeechConfig.Mode current = mode(playerId);
        int index = config.modes.indexOf(current);
        SpeechConfig.Mode next = config.modes.get((index + 1) % config.modes.size());
        modes.put(playerId, next.id);
        return next;
    }
}
