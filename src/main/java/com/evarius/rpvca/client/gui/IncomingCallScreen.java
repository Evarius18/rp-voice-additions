package com.evarius.rpvca.client.gui;

import com.evarius.rpvca.client.ClientActions;
import com.evarius.rpvca.client.ClientCommunicationState;
import com.evarius.rpvca.client.gui.component.HandheldButton;
import com.evarius.rpvca.client.gui.component.HandheldScreen;
import com.evarius.rpvca.network.CommunicationStatus;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/** Compact mouse-operable incoming-call card; it never pauses or blurs the world. */
public final class IncomingCallScreen extends HandheldScreen {
    private static final int CARD_WIDTH = 210;
    private static final int CARD_HEIGHT = 104;
    private final String callId;
    private boolean submitted;

    public IncomingCallScreen(String callId) {
        super(Text.translatable("gui.rp-vca.incoming.title"));
        this.callId = callId;
    }

    @Override
    protected void init() {
        positionHandheld(CARD_WIDTH, CARD_HEIGHT);
        int width = (deviceWidth - 30) / 2;
        addDrawableChild(new HandheldButton(deviceLeft + 10, deviceTop + deviceHeight - 31,
                width, 21, Text.translatable("gui.rp-vca.phone.answer"),
                HandheldButton.Style.PRIMARY, () -> submit("phone_answer")));
        addDrawableChild(new HandheldButton(deviceLeft + 20 + width, deviceTop + deviceHeight - 31,
                width, 21, Text.translatable("gui.rp-vca.phone.decline"),
                HandheldButton.Style.DANGER, () -> submit("phone_decline")));
    }

    private void submit(String action) {
        if (submitted) return;
        submitted = true;
        children().forEach(element -> {
            if (element instanceof net.minecraft.client.gui.widget.ClickableWidget widget) {
                widget.active = false;
            }
        });
        ClientActions.send(action, callId);
    }

    @Override
    public void tick() {
        CommunicationStatus status = ClientCommunicationState.get();
        if (!"incoming".equals(status.phoneState) || !callId.equals(status.phoneCallId)) close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderHandheldFrame(context);
        super.render(context, mouseX, mouseY, deltaTicks);
        CommunicationStatus status = ClientCommunicationState.get();
        context.drawText(textRenderer, title, deviceLeft + 12, deviceTop + 12, 0xFFFFD166, false);
        Text primary = status.phonePeer.isBlank()
                ? Text.translatable("gui.rp-vca.phone.unknown_number") : Text.literal(status.phonePeer);
        context.drawText(textRenderer, primary, deviceLeft + 12, deviceTop + 31, TEXT, false);
        if (status.phonePeerSavedContact && !status.phonePeerNumber.isBlank()) {
            context.drawText(textRenderer, status.phonePeerNumber, deviceLeft + 12,
                    deviceTop + 45, MUTED, false);
        }
    }
}
