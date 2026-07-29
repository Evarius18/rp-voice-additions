package com.evarius.rpvca.client.gui;

import com.evarius.rpvca.client.ClientActions;
import com.evarius.rpvca.client.ClientCommunicationState;
import com.evarius.rpvca.client.ClientConfigStore;
import com.evarius.rpvca.client.gui.component.HandheldButton;
import com.evarius.rpvca.client.gui.component.HandheldScreen;
import com.evarius.rpvca.network.CommunicationStatus;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Technical lower-right handheld radio with LCD, hardware controls and local gain. */
public final class RadioScreen extends HandheldScreen {
    private static final int PREFERRED_WIDTH = 232;
    private static final int PREFERRED_HEIGHT = 224;
    private int channelIndex;

    public RadioScreen() {
        super(Text.translatable("gui.rp-vca.radio.title"));
    }

    @Override
    protected void init() {
        positionHandheld(PREFERRED_WIDTH, PREFERRED_HEIGHT);
        List<CommunicationStatus.NamedEntry> channels = ClientCommunicationState.get().radioChannels;
        String current = ClientCommunicationState.get().radioChannel;
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).id().equals(current)) {
                channelIndex = i;
                break;
            }
        }

        int buttonY = deviceTop + 105;
        addDrawableChild(new HandheldButton(deviceLeft + 15, buttonY, 38, 23, Text.literal("<"),
                HandheldButton.Style.NORMAL, () -> changeChannel(-1)));
        addDrawableChild(new HandheldButton(deviceLeft + deviceWidth - 53, buttonY, 38, 23, Text.literal(">"),
                HandheldButton.Style.NORMAL, () -> changeChannel(1)));
        addDrawableChild(new HandheldButton(deviceLeft + 60, buttonY, deviceWidth - 120, 23,
                Text.translatable("gui.rp-vca.radio.channel"), HandheldButton.Style.PRIMARY,
                () -> changeChannel(1)));
        addDrawableChild(new HandheldButton(deviceLeft + 34, deviceTop + 137, deviceWidth - 68, 23,
                Text.translatable("gui.rp-vca.radio.tx"), HandheldButton.Style.DANGER,
                () -> ClientActions.send("radio_toggle")));
        addDrawableChild(new HandheldButton(deviceLeft + 34, deviceTop + 165, deviceWidth - 68, 20,
                Text.translatable("gui.rp-vca.radio.off"), HandheldButton.Style.NORMAL,
                () -> ClientActions.send("radio_off")));
        addDrawableChild(new RadioVolumeSlider(deviceLeft + 23, deviceTop + 190,
                deviceWidth - 46, 25, ClientConfigStore.get().radioVolume / 2.0D));
        ClientActions.send("status_request");
    }

    private void changeChannel(int direction) {
        List<CommunicationStatus.NamedEntry> channels = ClientCommunicationState.get().radioChannels;
        if (channels.isEmpty()) {
            return;
        }
        channelIndex = Math.floorMod(channelIndex + direction, channels.size());
        ClientActions.send("radio_tune", channels.get(channelIndex).id());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        renderHandheldFrame(context);
        renderRadioBody(context);
        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void renderRadioBody(DrawContext context) {
        CommunicationStatus status = ClientCommunicationState.get();
        int center = deviceLeft + deviceWidth / 2;

        // Antenna, power knob and speaker grille make the silhouette read as radio hardware.
        context.fill(deviceLeft + 21, deviceTop - 8, deviceLeft + 30, deviceTop + 16, 0xFF10171B);
        context.fill(deviceLeft + 19, deviceTop - 10, deviceLeft + 32, deviceTop - 6, 0xFF26363D);
        context.fill(deviceLeft + deviceWidth - 43, deviceTop + 7,
                deviceLeft + deviceWidth - 23, deviceTop + 13, 0xFF34444A);
        for (int i = 0; i < 4; i++) {
            context.fill(deviceLeft + deviceWidth - 52 + i * 6, deviceTop + 22,
                    deviceLeft + deviceWidth - 49 + i * 6, deviceTop + 34, 0xFF263940);
        }

        context.drawCenteredTextWithShadow(textRenderer, title, center, deviceTop + 17, TEXT);
        context.fill(deviceLeft + 20, deviceTop + 41, deviceLeft + deviceWidth - 20,
                deviceTop + 96, 0xFF789B8C);
        context.fill(deviceLeft + 23, deviceTop + 44, deviceLeft + deviceWidth - 23,
                deviceTop + 93, 0xFF9BC3B1);

        String channel = status.radioDisplayName.isBlank()
                ? Text.translatable("gui.rp-vca.radio.no_channel").getString() : status.radioDisplayName;
        String channelId = status.radioChannel.isBlank() ? "---" : status.radioChannel;
        String mode = status.radioTransmitting ? "TX" : status.radioChannel.isBlank() ? "OFF" : "RX";
        int modeColor = status.radioTransmitting ? 0xFF8B1717
                : status.radioChannel.isBlank() ? 0xFF4B5B53 : 0xFF174A31;

        context.drawCenteredTextWithShadow(textRenderer, channel, center, deviceTop + 53, 0xFF0A1A14);
        context.drawCenteredTextWithShadow(textRenderer, channelId, center, deviceTop + 67, 0xFF29483B);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal(mode), center, deviceTop + 81, modeColor);
        context.fill(deviceLeft + 27, deviceTop + 83, deviceLeft + 32, deviceTop + 88,
                status.radioTransmitting ? 0xFFFF584F : 0xFF49D17C);
    }

    private static final class RadioVolumeSlider extends SliderWidget {
        private RadioVolumeSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Text.empty(), value);
            updateMessage();
        }

        @Override
        public void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
            int barY = getY() + height - 6;
            context.fill(getX(), getY(), getX() + width, getY() + height, 0xFF172229);
            context.fill(getX() + 7, barY, getX() + width - 7, barY + 3, 0xFF3A5059);
            int available = width - 16;
            int knob = getX() + 7 + (int) Math.round(value * available);
            context.fill(getX() + 7, barY, knob, barY + 3, 0xFF65C7D8);
            context.fill(knob - 2, barY - 2, knob + 2, barY + 5, 0xFFE8F7FA);
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                    getX() + width / 2, getY() + 3, 0xFFE8F7FA);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable("gui.rp-vca.radio.volume", Math.round(value * 200.0D)));
        }

        @Override
        protected void applyValue() {
            ClientConfigStore.setRadioVolume(value * 2.0D);
        }
    }
}
