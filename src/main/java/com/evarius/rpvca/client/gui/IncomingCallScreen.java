package com.evarius.rpvca.client.gui;

import com.evarius.rpvca.client.ClientActions;
import com.evarius.rpvca.client.ClientCommunicationState;
import com.evarius.rpvca.client.gui.component.HandheldButton;
import com.evarius.rpvca.client.gui.component.HandheldScreen;
import com.evarius.rpvca.network.CommunicationStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Compact mouse-operable incoming-call card; it never pauses or blurs the world. */
public final class IncomingCallScreen extends HandheldScreen {
    private static final int CARD_WIDTH = 210;
    private static final int CARD_HEIGHT = 104;
    private final String callId;
    private boolean submitted;

    public IncomingCallScreen(String callId) {
        super(Component.translatable("gui.rp-vca.incoming.title"));
        this.callId = callId;
    }

    @Override
    protected void init() {
        positionHandheld(CARD_WIDTH, CARD_HEIGHT);
        int width = (deviceWidth - 30) / 2;
        addRenderableWidget(new HandheldButton(deviceLeft + 10, deviceTop + deviceHeight - 31,
                width, 21, Component.translatable("gui.rp-vca.phone.answer"),
                HandheldButton.Style.PRIMARY, () -> submit("phone_answer")));
        addRenderableWidget(new HandheldButton(deviceLeft + 20 + width, deviceTop + deviceHeight - 31,
                width, 21, Component.translatable("gui.rp-vca.phone.decline"),
                HandheldButton.Style.DANGER, () -> submit("phone_decline")));
    }

    private void submit(String action) {
        if (submitted) return;
        submitted = true;
        children().forEach(element -> {
            if (element instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                widget.active = false;
            }
        });
        ClientActions.send(action, callId);
    }

    @Override
    public void tick() {
        CommunicationStatus status = ClientCommunicationState.get();
        if (!"incoming".equals(status.phoneState) || !callId.equals(status.phoneCallId)) onClose();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float deltaTicks) {
        renderHandheldFrame(context);
        super.render(context, mouseX, mouseY, deltaTicks);
        CommunicationStatus status = ClientCommunicationState.get();
        context.drawString(font, title, deviceLeft + 12, deviceTop + 12, 0xFFFFD166, false);
        Component primary = status.phonePeer.isBlank()
                ? Component.translatable("gui.rp-vca.phone.unknown_number") : Component.literal(status.phonePeer);
        context.drawString(font, primary, deviceLeft + 12, deviceTop + 31, TEXT, false);
        if (status.phonePeerSavedContact && !status.phonePeerNumber.isBlank()) {
            context.drawString(font, status.phonePeerNumber, deviceLeft + 12,
                    deviceTop + 45, MUTED, false);
        }
    }
}
