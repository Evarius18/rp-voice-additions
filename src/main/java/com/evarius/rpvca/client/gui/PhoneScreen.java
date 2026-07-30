package com.evarius.rpvca.client.gui;

import com.evarius.rpvca.client.ClientActions;
import com.evarius.rpvca.client.ClientCommunicationState;
import com.evarius.rpvca.client.gui.component.HandheldButton;
import com.evarius.rpvca.client.gui.component.HandheldScreen;
import com.evarius.rpvca.network.CommunicationStatus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.time.LocalTime;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;

/**
 * TerraNexus-inspired portrait launcher anchored like a phone held in the lower-right hand.
 * Optional applications are entirely driven by the server-filtered integration list.
 */
public final class PhoneScreen extends HandheldScreen {
    private static final int PREFERRED_WIDTH = 216;
    private static final int PREFERRED_HEIGHT = 350;
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter HISTORY_TIME =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

    private Page page = Page.HOME;
    private TextFieldWidget dialField;
    private TextFieldWidget contactNameField;
    private TextFieldWidget contactNumberField;
    private int contentFingerprint;
    private boolean confirmHistoryClear;

    public PhoneScreen() {
        super(Text.translatable("gui.rp-vca.phone.title"));
    }

    @Override
    protected void init() {
        positionHandheld(PREFERRED_WIDTH, PREFERRED_HEIGHT);
        contentFingerprint = contentFingerprint();
        rebuild();
        ClientActions.send("status_request");
    }

    @Override
    public void tick() {
        super.tick();
        int currentFingerprint = contentFingerprint();
        if (currentFingerprint != contentFingerprint) {
            contentFingerprint = currentFingerprint;
            if (page == Page.APPS && ClientCommunicationState.get().phoneApps.isEmpty()) {
                page = Page.HOME;
            }
            if (page == Page.HOME || page == Page.APPS
                    || page == Page.CONTACTS || page == Page.HISTORY) {
                rebuild();
            }
        }
    }

    private void rebuild() {
        clearChildren();
        dialField = null;
        contactNameField = null;
        contactNumberField = null;
        if (page != Page.HOME) {
            addThemedButton(deviceLeft + 12, deviceTop + deviceHeight - 26, 42, 18,
                    Text.translatable("gui.rp-vca.phone.back"), HandheldButton.Style.NORMAL,
                    () -> openPage(Page.HOME));
        }
        switch (page) {
            case HOME -> buildLauncher();
            case DIALER -> buildDialer();
            case CONTACTS -> buildContacts();
            case EMERGENCY -> buildEmergency();
            case HISTORY -> buildHistory();
            case APPS -> buildApps();
        }
    }

    private void buildLauncher() {
        int gap = 8;
        int tileWidth = (deviceWidth - 32) / 2;
        int x1 = deviceLeft + 12;
        int x2 = x1 + tileWidth + gap;
        int y1 = deviceTop + 88;
        int rows = ClientCommunicationState.get().phoneApps.isEmpty() ? 2 : 3;
        int tileHeight = Math.clamp((deviceHeight - 112 - (rows - 1) * gap) / rows, 28, 46);
        int y2 = y1 + tileHeight + gap;
        addThemedButton(x1, y1, tileWidth, tileHeight, Text.translatable("gui.rp-vca.phone.app.phone"),
                HandheldButton.Style.APP, () -> openPage(Page.DIALER));
        addThemedButton(x2, y1, tileWidth, tileHeight, Text.translatable("gui.rp-vca.phone.app.contacts"),
                HandheldButton.Style.APP, () -> openPage(Page.CONTACTS));
        addThemedButton(x1, y2, tileWidth, tileHeight, Text.translatable("gui.rp-vca.phone.app.emergency"),
                HandheldButton.Style.APP, () -> openPage(Page.EMERGENCY));
        addThemedButton(x2, y2, tileWidth, tileHeight, Text.translatable("gui.rp-vca.phone.app.history"),
                HandheldButton.Style.APP, () -> openPage(Page.HISTORY));
        if (!ClientCommunicationState.get().phoneApps.isEmpty()) {
            addThemedButton(x1, y2 + tileHeight + gap, tileWidth, tileHeight,
                    Text.translatable("gui.rp-vca.phone.app.terranexus"),
                    HandheldButton.Style.APP, () -> openPage(Page.APPS));
        }
    }

    private void buildDialer() {
        int contentLeft = deviceLeft + 14;
        int contentWidth = deviceWidth - 28;
        int y = deviceTop + 98;
        dialField = new TextFieldWidget(textRenderer, contentLeft, y, contentWidth, 20,
                Text.translatable("gui.rp-vca.phone.destination"));
        dialField.setMaxLength(64);
        addDrawableChild(dialField);
        int buttonWidth = (contentWidth - 8) / 2;
        addThemedButton(contentLeft, y + 28, buttonWidth, 22,
                Text.translatable("gui.rp-vca.phone.call"), HandheldButton.Style.PRIMARY,
                () -> ClientActions.send("phone_call", dialField.getText().trim()));
        addThemedButton(contentLeft + buttonWidth + 8, y + 28, buttonWidth, 22,
                Text.translatable("gui.rp-vca.phone.answer"), HandheldButton.Style.PRIMARY,
                () -> ClientActions.send("phone_answer", ClientCommunicationState.get().phoneCallId));
        addThemedButton(contentLeft, y + 58, buttonWidth, 22,
                Text.translatable("gui.rp-vca.phone.decline"), HandheldButton.Style.DANGER,
                () -> ClientActions.send("phone_decline", ClientCommunicationState.get().phoneCallId));
        addThemedButton(contentLeft + buttonWidth + 8, y + 58, buttonWidth, 22,
                Text.translatable("gui.rp-vca.phone.hangup"), HandheldButton.Style.DANGER,
                () -> ClientActions.send("phone_hangup"));
        addThemedButton(contentLeft, y + 88, contentWidth, 22,
                Text.translatable("gui.rp-vca.phone.speaker"), HandheldButton.Style.NORMAL,
                () -> ClientActions.send("phone_speaker"));
    }

    private void buildContacts() {
        int contentLeft = deviceLeft + 12;
        int contentWidth = deviceWidth - 24;
        contactNameField = new TextFieldWidget(textRenderer, contentLeft, deviceTop + 91,
                contentWidth, 18, Text.translatable("gui.rp-vca.phone.contact.name"));
        contactNameField.setMaxLength(64);
        addDrawableChild(contactNameField);
        contactNumberField = new TextFieldWidget(textRenderer, contentLeft, deviceTop + 113,
                contentWidth, 18, Text.translatable("gui.rp-vca.phone.contact.number"));
        contactNumberField.setMaxLength(24);
        addDrawableChild(contactNumberField);
        addThemedButton(contentLeft, deviceTop + 135, contentWidth, 19,
                Text.translatable("gui.rp-vca.phone.contact.save"), HandheldButton.Style.PRIMARY,
                () -> ClientActions.send("contact_upsert",
                        contactNameField.getText().trim() + "\t" + contactNumberField.getText().trim()));
        int y = deviceTop + 159;
        int bottom = deviceTop + deviceHeight - 34;
        for (Map.Entry<String, String> contact : ClientCommunicationState.get().contacts.entrySet()) {
            if (y + 23 > bottom) {
                break;
            }
            String label = trim(contact.getKey() + " · " + contact.getValue(), deviceWidth - 62);
            addThemedButton(deviceLeft + 12, y, deviceWidth - 55, 22, Text.literal(label),
                    HandheldButton.Style.NORMAL, () -> ClientActions.send("phone_call", contact.getValue()));
            addThemedButton(deviceLeft + deviceWidth - 38, y, 26, 22, Text.literal("×"),
                    HandheldButton.Style.DANGER, () -> ClientActions.send("contact_remove", contact.getKey()));
            y += 27;
        }
    }

    private void buildHistory() {
        int y = deviceTop + 92;
        int bottom = deviceTop + deviceHeight - 58;
        for (com.evarius.rpvca.phone.history.CallHistoryEntryView entry
                : ClientCommunicationState.get().callHistory) {
            if (y + 23 > bottom) {
                break;
            }
            String direction = entry.direction() == com.evarius.rpvca.phone.history.CallDirection.INCOMING
                    ? "←" : "→";
            String display = entry.remoteDisplayName().isBlank()
                    ? entry.remoteNumber() : entry.remoteDisplayName();
            String result = Text.translatable("gui.rp-vca.phone.history.status."
                    + entry.status().name().toLowerCase(java.util.Locale.ROOT)).getString();
            String label = trim(direction + " " + display + " · " + result + " · "
                    + HISTORY_TIME.format(Instant.ofEpochMilli(entry.startedAt())), deviceWidth - 62);
            addThemedButton(deviceLeft + 12, y, deviceWidth - 55, 22, Text.literal(label),
                    HandheldButton.Style.NORMAL,
                    () -> ClientActions.send("phone_call", entry.remoteNumber()));
            addThemedButton(deviceLeft + deviceWidth - 38, y, 26, 22, Text.literal("×"),
                    HandheldButton.Style.DANGER,
                    () -> ClientActions.send("history_remove", entry.entryId().toString()));
            y += 27;
        }
        addThemedButton(deviceLeft + 60, deviceTop + deviceHeight - 52, deviceWidth - 72, 19,
                Text.translatable(confirmHistoryClear
                        ? "gui.rp-vca.phone.history.confirm_clear"
                        : "gui.rp-vca.phone.history.clear"),
                HandheldButton.Style.DANGER, () -> {
                    if (confirmHistoryClear) {
                        ClientActions.send("history_clear");
                        confirmHistoryClear = false;
                    } else {
                        confirmHistoryClear = true;
                        rebuild();
                    }
                });
    }

    private void buildEmergency() {
        int y = deviceTop + 92;
        int bottom = deviceTop + deviceHeight - 34;
        for (CommunicationStatus.NamedEntry number : ClientCommunicationState.get().emergencyNumbers) {
            if (y + 25 > bottom) {
                break;
            }
            addThemedButton(deviceLeft + 12, y, deviceWidth - 24, 24,
                    Text.literal(number.id() + " · " + number.name()), HandheldButton.Style.DANGER,
                    () -> ClientActions.send("phone_call", number.id()));
            y += 30;
        }
    }

    private void buildApps() {
        int y = deviceTop + 92;
        for (CommunicationStatus.NamedEntry app : ClientCommunicationState.get().phoneApps) {
            addThemedButton(deviceLeft + 12, y, deviceWidth - 24, 42, Text.literal(app.name()),
                    HandheldButton.Style.APP, () -> ClientActions.send("compat_open", app.id()));
            y += 50;
        }
    }

    private void openPage(Page target) {
        page = target;
        confirmHistoryClear = false;
        rebuild();
    }

    private int contentFingerprint() {
        CommunicationStatus status = ClientCommunicationState.get();
        return Objects.hash(status.phoneApps, status.contacts, status.callHistory);
    }

    private String trim(String value, int maxWidth) {
        return textRenderer.trimToWidth(value, Math.max(20, maxWidth));
    }

    private void addThemedButton(int x, int y, int buttonWidth, int buttonHeight, Text label,
                                 HandheldButton.Style style, Runnable action) {
        addDrawableChild(new HandheldButton(x, y, buttonWidth, buttonHeight, label, style, action));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderHandheldFrame(context);
        renderPhoneChrome(context);
        super.render(context, mouseX, mouseY, deltaTicks);
        context.fill(deviceLeft + deviceWidth / 2 - 25, deviceTop + deviceHeight - 9,
                deviceLeft + deviceWidth / 2 + 25, deviceTop + deviceHeight - 6, 0xFF8FA5AB);
    }

    private void renderPhoneChrome(DrawContext context) {
        CommunicationStatus status = ClientCommunicationState.get();
        int innerLeft = deviceLeft + 7;
        int innerRight = deviceLeft + deviceWidth - 7;
        context.fill(innerLeft, deviceTop + 7, innerRight, deviceTop + 29, 0xFF0A151D);
        context.fill(innerLeft, deviceTop + 30, innerRight, deviceTop + deviceHeight - 16, SURFACE);
        context.fill(deviceLeft + deviceWidth / 2 - 18, deviceTop + 4,
                deviceLeft + deviceWidth / 2 + 18, deviceTop + 6, 0xFF344852);

        context.drawText(textRenderer, LocalTime.now().format(CLOCK), innerLeft + 7,
                deviceTop + 14, TEXT, false);
        String ecosystem = status.phoneApps.isEmpty() ? "RP" : "TN";
        context.drawText(textRenderer, ecosystem, innerRight - 34, deviceTop + 14, MUTED, false);
        context.drawText(textRenderer, status.phoneCoverage ? "▮▮▮" : "×",
                innerRight - 17, deviceTop + 14, status.phoneCoverage ? ACCENT : 0xFFFF6B73, false);

        context.drawText(textRenderer, title, innerLeft + 9, deviceTop + 38, TEXT, false);
        context.drawText(textRenderer, pageTitle(), innerLeft + 9, deviceTop + 53, ACCENT, false);
        context.fill(innerLeft + 8, deviceTop + 69, innerRight - 8, deviceTop + 70, 0xFF31505B);

        String state = Text.translatable("gui.rp-vca.phone.state." + status.phoneState).getString();
        String peer = status.phonePeer.isBlank() ? status.phoneNumber : status.phonePeer;
        String statusLine = peer.isBlank() ? state : state + " · " + peer;
        context.drawText(textRenderer, statusLine, innerLeft + 9, deviceTop + 73,
                status.phoneState.equals("incoming") ? 0xFFFFD166 : MUTED, false);
        if (!status.phoneCoverage) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("gui.rp-vca.phone.no_network"),
                    deviceLeft + deviceWidth / 2, deviceTop + deviceHeight - 43, 0xFFFF6B73);
        } else if (!status.phoneNotice.isBlank()) {
            Text notice = status.phoneNotice.startsWith("notice.rp-vca.")
                    ? Text.translatable(status.phoneNotice) : Text.literal(status.phoneNotice);
            context.drawCenteredTextWithShadow(textRenderer, notice,
                    deviceLeft + deviceWidth / 2, deviceTop + deviceHeight - 43, 0xFFFFD166);
        }
        boolean empty = (page == Page.CONTACTS && status.contacts.isEmpty())
                || (page == Page.HISTORY && status.callHistory.isEmpty())
                || (page == Page.APPS && status.phoneApps.isEmpty());
        if (empty) {
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("gui.rp-vca.phone.empty"),
                    deviceLeft + deviceWidth / 2, deviceTop + 112, MUTED);
        }
    }

    private Text pageTitle() {
        return Text.translatable(switch (page) {
            case HOME -> "gui.rp-vca.phone.page.home";
            case DIALER -> "gui.rp-vca.phone.page.dialer";
            case CONTACTS -> "gui.rp-vca.phone.page.contacts";
            case EMERGENCY -> "gui.rp-vca.phone.page.emergency";
            case HISTORY -> "gui.rp-vca.phone.page.history";
            case APPS -> "gui.rp-vca.phone.page.apps";
        });
    }

    private enum Page {
        HOME, DIALER, CONTACTS, EMERGENCY, HISTORY, APPS
    }
}
