package com.evarius.rpvca.client;

import com.evarius.rpvca.network.CommunicationStatus;
import com.google.gson.Gson;

public final class ClientCommunicationState {
    private static final Gson GSON = new Gson();
    private static volatile CommunicationStatus status = new CommunicationStatus();
    private static volatile long revision;

    private ClientCommunicationState() {
    }

    public static CommunicationStatus get() {
        return status;
    }

    public static long revision() {
        return revision;
    }

    public static void update(String json) {
        CommunicationStatus decoded = GSON.fromJson(json, CommunicationStatus.class);
        if (decoded != null) {
            status = decoded;
            revision++;
        }
    }
}
