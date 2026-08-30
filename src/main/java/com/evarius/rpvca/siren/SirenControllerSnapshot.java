package com.evarius.rpvca.siren;

import java.util.ArrayList;
import java.util.List;

/** Server-authored controller state consumed by the client screen. */
public final class SirenControllerSnapshot {
    public String controllerId = "";
    public String name = "";
    public int linkedSirens;
    public boolean active;
    public boolean live;
    public boolean recording;
    public String notice = "";
    public List<NamedOption> scenarios = new ArrayList<>();
    public List<ScheduledOption> scheduled = new ArrayList<>();
    public List<NamedOption> announcements = new ArrayList<>();

    public record NamedOption(String id, String name) {
    }

    public record ScheduledOption(String id, String scenarioId, long executeAt) {
    }
}
