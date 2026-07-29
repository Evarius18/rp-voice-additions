package com.evarius.rpvca.client.gui.component;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Shared non-pausing base for handheld devices.
 *
 * <p>The screen deliberately renders neither an in-game gradient nor blur. Only the device
 * itself and a small drop shadow are drawn over the unchanged world.</p>
 */
public abstract class HandheldScreen extends Screen {
    protected static final int OUTER = 0xFF071018;
    protected static final int BODY = 0xFF101C24;
    protected static final int SURFACE = 0xFF102A36;
    protected static final int SURFACE_ALT = 0xFF153845;
    protected static final int ACCENT = 0xFF65C7D8;
    protected static final int TEXT = 0xFFE8F7FA;
    protected static final int MUTED = 0xFF8FB5BE;

    protected int deviceLeft;
    protected int deviceTop;
    protected int deviceWidth;
    protected int deviceHeight;

    protected HandheldScreen(Text title) {
        super(title);
    }

    protected final void positionHandheld(int preferredWidth, int preferredHeight) {
        deviceWidth = Math.min(preferredWidth, Math.max(1, width - 16));
        deviceHeight = Math.min(preferredHeight, Math.max(1, height - 16));
        deviceLeft = Math.max(6, width - deviceWidth - 10);
        deviceTop = Math.max(6, height - deviceHeight - 10);
    }

    protected final void renderHandheldFrame(DrawContext context) {
        context.fill(deviceLeft - 5, deviceTop + 6, deviceLeft + deviceWidth + 4,
                deviceTop + deviceHeight + 6, 0x55000000);
        context.fill(deviceLeft + 5, deviceTop, deviceLeft + deviceWidth - 5,
                deviceTop + deviceHeight, OUTER);
        context.fill(deviceLeft, deviceTop + 6, deviceLeft + deviceWidth,
                deviceTop + deviceHeight - 6, OUTER);
        context.fill(deviceLeft + 6, deviceTop + 7, deviceLeft + deviceWidth - 6,
                deviceTop + deviceHeight - 7, BODY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Intentionally empty: handheld screens must never obscure or blur the game world.
    }

    @Override
    protected void applyBlur(DrawContext context) {
        // Screen#renderBackground normally calls this in-world. This device opts out explicitly.
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
