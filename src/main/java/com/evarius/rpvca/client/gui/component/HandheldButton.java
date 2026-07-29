package com.evarius.rpvca.client.gui.component;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/** Small themed button shared by the phone and radio without coupling their screen logic. */
public final class HandheldButton extends PressableWidget {
    private final Runnable action;
    private final Style style;

    public HandheldButton(int x, int y, int width, int height, Text message, Style style, Runnable action) {
        super(x, y, width, height, message);
        this.style = style;
        this.action = action;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int background = !active ? 0xFF1A252B : isHovered() ? style.hover : style.background;
        int border = isHovered() && active ? style.accent : 0xFF334952;
        context.fill(getX(), getY(), getX() + width, getY() + height, border);
        context.fill(getX() + 1, getY() + 1, getX() + width - 1, getY() + height - 1, background);
        if (style == Style.APP) {
            context.fill(getX() + 1, getY() + 1, getX() + 4, getY() + height - 1, style.accent);
        }
        int color = active ? 0xFFE8F7FA : 0xFF71838A;
        context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                getX() + width / 2,
                getY() + (height - 8) / 2, color);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }

    public enum Style {
        APP(0xFF153845, 0xFF1E4A59, 0xFF65C7D8),
        NORMAL(0xFF202D34, 0xFF2B3D45, 0xFF65C7D8),
        PRIMARY(0xFF185365, 0xFF237085, 0xFF7BE1EE),
        DANGER(0xFF592D34, 0xFF743A43, 0xFFFF7887);

        private final int background;
        private final int hover;
        private final int accent;

        Style(int background, int hover, int accent) {
            this.background = background;
            this.hover = hover;
            this.accent = accent;
        }
    }
}
