package com.evarius.rpvca.config;

import java.util.ArrayList;
import java.util.List;

/** Item assignments are resolved after registries are available. Invalid IDs are ignored. */
public final class DeviceConfig {
    public List<String> phoneItems = new ArrayList<>(List.of("rp-vca:mobile_phone"));
    public List<String> radioItems = new ArrayList<>(List.of("rp-vca:radio"));
    public boolean openGuiOnItemUse = true;
}
