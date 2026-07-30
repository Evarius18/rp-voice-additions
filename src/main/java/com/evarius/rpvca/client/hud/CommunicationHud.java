package com.evarius.rpvca.client.hud;

import com.evarius.rpvca.RpVoiceAddon;
import com.evarius.rpvca.client.ClientCommunicationState;
import com.evarius.rpvca.config.HudConfig;
import com.evarius.rpvca.network.CommunicationStatus;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class CommunicationHud {
    private CommunicationHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, RpVoiceAddon.id("communication_hud"),
                (context, tickCounter) -> {
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.options.hudHidden) return;
                    HudConfig config = RpVoiceAddon.configs().hud();
                    if (!config.enabled) return;
                    CommunicationStatus status = ClientCommunicationState.get();
                    List<Line> lines = lines(config, status);
                    if (lines.isEmpty()) return;
                    int widest = lines.stream().mapToInt(line -> client.textRenderer.getWidth(line.text())).max().orElse(0);
                    int boxWidth = widest + 12;
                    int boxHeight = lines.size() * 12 + 8;
                    int x = "left".equals(config.horizontalAnchor) ? config.offsetX
                            : client.getWindow().getScaledWidth() - config.offsetX - boxWidth;
                    int y = config.offsetY;
                    context.fill(x, y, x + boxWidth, y + boxHeight, 0xA510151B);
                    context.fill(x, y, x + 2, y + boxHeight, 0xFF39AFC2);
                    for (int index = 0; index < lines.size(); index++) {
                        Line line = lines.get(index);
                        context.drawText(client.textRenderer, line.text(), x + 7, y + 5 + index * 12,
                                line.color(), true);
                    }
                });
    }

    private static List<Line> lines(HudConfig config, CommunicationStatus status) {
        List<Line> lines = new ArrayList<>();
        if (config.showSpeech) {
            boolean whisper = "whisper".equalsIgnoreCase(status.speechMode);
            Text suffix = whisper
                    ? (com.evarius.rpvca.client.compatibility.svc.SvcWhisperCompatibility.isNativeWhispering()
                    ? Text.translatable("hud.rp-vca.speech.svc")
                    : Text.translatable("hud.rp-vca.speech.svc_key"))
                    : config.showSpeechRange
                    ? Text.translatable("hud.rp-vca.speech.range", Math.round(status.speechDistance))
                    : Text.empty();
            lines.add(new Line(Text.literal("◉ ").append(Text.translatable(status.speechTranslationKey))
                    .append(suffix), 0xFFE9F7FA));
        }
        if (config.showPhone && (!config.hideInactivePhone || !"idle".equals(status.phoneState))) {
            String phone = switch (status.phoneState) {
                case "incoming" -> "☎ " + Text.translatable("hud.rp-vca.phone.incoming").getString()
                        + " · " + status.phonePeer;
                case "ringing" -> "☎ " + Text.translatable("hud.rp-vca.phone.ringing").getString()
                        + " · " + status.phonePeer;
                case "active" -> "☎ " + status.phonePeer + (status.phoneSpeaker ? " · LS" : "");
                default -> "☎ " + (status.phoneCoverage ? status.phoneNumber
                        : Text.translatable("hud.rp-vca.phone.no_network").getString());
            };
            lines.add(new Line(Text.literal(phone), "incoming".equals(status.phoneState) ? 0xFF72F0A5 : 0xFF9EDBE5));
        }
        if (config.showPhone && status.phoneNotice != null && !status.phoneNotice.isBlank()) {
            Text notice = status.phoneNotice.startsWith("notice.rp-vca.")
                    ? Text.translatable(status.phoneNotice) : Text.literal(status.phoneNotice);
            lines.add(new Line(Text.translatable("hud.rp-vca.phone.notice", notice), 0xFFFFC766));
        }
        if (config.showPhone && "active".equals(status.phoneState) && !status.phoneHeld) {
            lines.add(new Line(Text.translatable("hud.rp-vca.phone.must_be_held"), 0xFFFFC766));
        }
        if (shouldRenderRadioHud(status, config)) {
            boolean radioActive = isRadioActive(status);
            String channel = radioActive ? status.radioDisplayName
                    : Text.translatable("hud.rp-vca.radio.off").getString();
            lines.add(new Line(Text.literal("⌁ " + channel + " · " + (status.radioTransmitting ? "TX" : "RX")),
                    status.radioTransmitting ? 0xFFFF7657 : 0xFFB5D4A7));
        }
        return lines;
    }

    static boolean shouldRenderRadioHud(CommunicationStatus status, HudConfig config) {
        // A disabled item requirement affects routing only. It must not make the
        // HUD claim that the player actually carries a radio.
        return config.showRadio && status.radioAvailable
                && (!config.hideInactiveRadio || isRadioActive(status));
    }

    private static boolean isRadioActive(CommunicationStatus status) {
        return status.radioEnabled && status.radioPoweredOn
                && status.radioAvailable && !status.radioChannel.isBlank();
    }

    private record Line(Text text, int color) {
    }
}
