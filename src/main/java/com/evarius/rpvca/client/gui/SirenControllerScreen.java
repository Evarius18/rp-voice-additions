package com.evarius.rpvca.client.gui;

import com.evarius.rpvca.client.ClientActions;
import com.evarius.rpvca.client.gui.component.HandheldButton;
import com.evarius.rpvca.siren.SirenControllerSnapshot;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Mouse-operated, non-pausing control terminal. It deliberately does not blur the world. */
public final class SirenControllerScreen extends Screen {
    private static final int PANEL_WIDTH = 390;
    private static final int PANEL_HEIGHT = 232;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd.MM. HH:mm")
            .withZone(ZoneId.systemDefault());

    private SirenControllerSnapshot snapshot;
    private int left;
    private int top;
    private int panelWidth;
    private int scenarioIndex;
    private int announcementIndex;
    private TextFieldWidget scheduleField;
    private TextFieldWidget recordingNameField;
    private String retainedSchedule = "";
    private String retainedRecordingName = "";
    private Page page = Page.CONTROL;

    public SirenControllerScreen(SirenControllerSnapshot snapshot) {
        super(Text.translatable("gui.rp-vca.siren.title"));
        this.snapshot = snapshot;
    }

    public void updateSnapshot(SirenControllerSnapshot updated) {
        String selectedScenario = selectedScenario() == null ? "" : selectedScenario().id();
        String selectedAnnouncement = selectedAnnouncement() == null ? "" : selectedAnnouncement().id();
        if (scheduleField != null) retainedSchedule = scheduleField.getText();
        if (recordingNameField != null) retainedRecordingName = recordingNameField.getText();
        snapshot = updated;
        scenarioIndex = indexOf(snapshot.scenarios, selectedScenario);
        announcementIndex = indexOf(snapshot.announcements, selectedAnnouncement);
        if (client != null) clearAndInit();
    }

    @Override
    protected void init() {
        panelWidth = Math.min(PANEL_WIDTH, width - 12);
        left = (width - panelWidth) / 2;
        top = Math.max(4, (height - PANEL_HEIGHT) / 2);
        int contentLeft = left + 18;
        int contentWidth = panelWidth - 36;

        int tabWidth = (contentWidth - 4) / 2;
        addDrawableChild(new HandheldButton(contentLeft, top + 46, tabWidth, 18,
                Text.translatable("gui.rp-vca.siren.tab.control"),
                page == Page.CONTROL ? HandheldButton.Style.PRIMARY : HandheldButton.Style.NORMAL,
                () -> changePage(Page.CONTROL)));
        addDrawableChild(new HandheldButton(contentLeft + tabWidth + 4, top + 46,
                contentWidth - tabWidth - 4, 18,
                Text.translatable("gui.rp-vca.siren.tab.announcements"),
                page == Page.ANNOUNCEMENTS ? HandheldButton.Style.PRIMARY : HandheldButton.Style.NORMAL,
                () -> changePage(Page.ANNOUNCEMENTS)));

        if (page == Page.ANNOUNCEMENTS) {
            initAnnouncementPage(contentLeft, contentWidth);
            return;
        }

        addDrawableChild(new HandheldButton(contentLeft, top + 82, 28, 20, Text.literal("<"),
                HandheldButton.Style.NORMAL, () -> changeScenario(-1)));
        addDrawableChild(new HandheldButton(contentLeft + 32, top + 82, contentWidth - 64, 20,
                Text.translatable("gui.rp-vca.siren.trigger"), HandheldButton.Style.DANGER,
                () -> sendForScenario("siren_trigger")));
        addDrawableChild(new HandheldButton(contentLeft + contentWidth - 28, top + 82, 28, 20,
                Text.literal(">"), HandheldButton.Style.NORMAL, () -> changeScenario(1)));

        addDrawableChild(new HandheldButton(contentLeft, top + 106, (contentWidth - 6) / 2, 20,
                Text.translatable("gui.rp-vca.siren.stop"), HandheldButton.Style.DANGER,
                () -> ClientActions.send("siren_stop")));
        addDrawableChild(new HandheldButton(contentLeft + (contentWidth + 6) / 2, top + 106,
                (contentWidth - 6) / 2, 20, Text.translatable("gui.rp-vca.siren.link"),
                HandheldButton.Style.PRIMARY, () -> ClientActions.send("siren_link")));

        scheduleField = new TextFieldWidget(textRenderer, contentLeft, top + 153,
                contentWidth - 112, 20, Text.translatable("gui.rp-vca.siren.schedule_input"));
        scheduleField.setMaxLength(16);
        scheduleField.setPlaceholder(Text.translatable("gui.rp-vca.siren.schedule_placeholder"));
        scheduleField.setText(retainedSchedule);
        addDrawableChild(scheduleField);
        addDrawableChild(new HandheldButton(contentLeft + contentWidth - 106, top + 153, 106, 20,
                Text.translatable("gui.rp-vca.siren.schedule"), HandheldButton.Style.PRIMARY,
                () -> {
                    SirenControllerSnapshot.NamedOption selected = selectedScenario();
                    if (selected != null) ClientActions.send("siren_schedule",
                            selected.id() + "\t" + scheduleField.getText());
                }));
        addDrawableChild(new HandheldButton(contentLeft, top + 177, contentWidth, 18,
                Text.translatable("gui.rp-vca.siren.cancel_next"), HandheldButton.Style.NORMAL,
                this::cancelNextSchedule));
    }

    private void initAnnouncementPage(int contentLeft, int contentWidth) {
        addDrawableChild(new HandheldButton(contentLeft, top + 76, contentWidth, 20,
                Text.translatable(snapshot.live ? "gui.rp-vca.siren.live_stop" : "gui.rp-vca.siren.live_start"),
                snapshot.live ? HandheldButton.Style.DANGER : HandheldButton.Style.PRIMARY,
                () -> ClientActions.send("siren_live")));
        recordingNameField = new TextFieldWidget(textRenderer, contentLeft, top + 104, contentWidth, 20,
                Text.translatable("gui.rp-vca.siren.recording_name"));
        recordingNameField.setMaxLength(48);
        recordingNameField.setPlaceholder(Text.translatable("gui.rp-vca.siren.recording_name"));
        recordingNameField.setText(retainedRecordingName);
        addDrawableChild(recordingNameField);
        addDrawableChild(new HandheldButton(contentLeft, top + 128, contentWidth, 20,
                Text.translatable(snapshot.recording ? "gui.rp-vca.siren.record_stop"
                        : "gui.rp-vca.siren.record_start"),
                snapshot.recording ? HandheldButton.Style.DANGER : HandheldButton.Style.PRIMARY,
                () -> ClientActions.send("siren_record", recordingNameField.getText())));

        int third = (contentWidth - 8) / 3;
        addDrawableChild(new HandheldButton(contentLeft, top + 181, third, 20, Text.literal("<"),
                HandheldButton.Style.NORMAL, () -> changeAnnouncement(-1)));
        addDrawableChild(new HandheldButton(contentLeft + third + 4, top + 181, third, 20,
                Text.translatable("gui.rp-vca.siren.play_recording"), HandheldButton.Style.PRIMARY,
                this::playAnnouncement));
        addDrawableChild(new HandheldButton(contentLeft + (third + 4) * 2, top + 181,
                contentWidth - (third + 4) * 2, 20, Text.translatable("gui.rp-vca.siren.delete_recording"),
                HandheldButton.Style.DANGER, this::removeAnnouncement));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(left - 2, top - 2, left + panelWidth + 2, top + PANEL_HEIGHT + 2, 0xFF7B8589);
        context.fill(left, top, left + panelWidth, top + PANEL_HEIGHT, 0xFF202A2E);
        context.fill(left + 10, top + 10, left + panelWidth - 10, top + 45, 0xFF10242C);
        context.drawText(textRenderer, title, left + 18, top + 17, 0xFF86E7EF, false);
        context.drawText(textRenderer, localizedOrLiteral(snapshot.name), left + 18, top + 31, 0xFFD6E7EA, false);
        Text state = Text.translatable(snapshot.active ? "gui.rp-vca.siren.active" : "gui.rp-vca.siren.ready");
        context.drawText(textRenderer, state, left + panelWidth - 18 - textRenderer.getWidth(state),
                top + 18, snapshot.active ? 0xFFFF6B62 : 0xFF67D88B, false);
        context.drawText(textRenderer, Text.translatable("gui.rp-vca.siren.linked_count", snapshot.linkedSirens),
                left + panelWidth - 18 - 100, top + 32, 0xFF9CB0B5, false);

        if (page == Page.CONTROL) {
            context.drawText(textRenderer, optionText(selectedScenario()), left + 18, top + 70,
                    0xFFF2C45C, false);
            context.drawText(textRenderer, Text.translatable("gui.rp-vca.siren.schedule_heading"),
                    left + 18, top + 139, 0xFFB9CCD0, false);
            String next = snapshot.scheduled.isEmpty() ? Text.translatable("gui.rp-vca.siren.none").getString()
                    : TIME_FORMAT.format(Instant.ofEpochMilli(snapshot.scheduled.getFirst().executeAt()));
            context.drawText(textRenderer, Text.translatable("gui.rp-vca.siren.next_alarm", next),
                    left + 18, top + 202, 0xFF9CB0B5, false);
        } else {
            context.drawText(textRenderer, Text.translatable("gui.rp-vca.siren.announcements"),
                    left + 18, top + 158, 0xFFB9CCD0, false);
            context.drawText(textRenderer, optionText(selectedAnnouncement()), left + 18, top + 169,
                    0xFFD6E7EA, false);
        }
        if (snapshot.notice != null && !snapshot.notice.isBlank()) {
            context.drawText(textRenderer, Text.translatable(snapshot.notice), left + 18,
                    top + PANEL_HEIGHT - 14, 0xFFF2C45C, false);
        }
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void changeScenario(int direction) {
        if (!snapshot.scenarios.isEmpty()) {
            scenarioIndex = Math.floorMod(scenarioIndex + direction, snapshot.scenarios.size());
        }
    }

    private void changePage(Page target) {
        if (page == target) return;
        if (scheduleField != null) retainedSchedule = scheduleField.getText();
        if (recordingNameField != null) retainedRecordingName = recordingNameField.getText();
        page = target;
        clearAndInit();
    }

    private void changeAnnouncement(int direction) {
        if (!snapshot.announcements.isEmpty()) {
            announcementIndex = Math.floorMod(announcementIndex + direction, snapshot.announcements.size());
        }
    }

    private void sendForScenario(String action) {
        SirenControllerSnapshot.NamedOption option = selectedScenario();
        if (option != null) ClientActions.send(action, option.id());
    }

    private void cancelNextSchedule() {
        if (!snapshot.scheduled.isEmpty()) ClientActions.send("siren_cancel", snapshot.scheduled.getFirst().id());
    }

    private void playAnnouncement() {
        SirenControllerSnapshot.NamedOption option = selectedAnnouncement();
        if (option != null) ClientActions.send("siren_announcement", option.id());
    }

    private void removeAnnouncement() {
        SirenControllerSnapshot.NamedOption option = selectedAnnouncement();
        if (option != null) ClientActions.send("siren_announcement_remove", option.id());
    }

    private SirenControllerSnapshot.NamedOption selectedScenario() {
        return snapshot.scenarios.isEmpty() ? null
                : snapshot.scenarios.get(Math.clamp(scenarioIndex, 0, snapshot.scenarios.size() - 1));
    }

    private SirenControllerSnapshot.NamedOption selectedAnnouncement() {
        return snapshot.announcements.isEmpty() ? null
                : snapshot.announcements.get(Math.clamp(announcementIndex, 0, snapshot.announcements.size() - 1));
    }

    private Text optionText(SirenControllerSnapshot.NamedOption option) {
        if (option == null) return Text.translatable("gui.rp-vca.siren.none");
        return option.name().startsWith("scenario.") || option.name().startsWith("signal.")
                ? Text.translatable(option.name()) : Text.literal(option.name());
    }

    private Text localizedOrLiteral(String value) {
        return value != null && (value.startsWith("block.") || value.startsWith("gui.")
                || value.startsWith("scenario.") || value.startsWith("signal."))
                ? Text.translatable(value) : Text.literal(value == null ? "" : value);
    }

    private static int indexOf(List<SirenControllerSnapshot.NamedOption> options, String id) {
        for (int index = 0; index < options.size(); index++) if (options.get(index).id().equals(id)) return index;
        return 0;
    }

    private enum Page {
        CONTROL,
        ANNOUNCEMENTS
    }
}
