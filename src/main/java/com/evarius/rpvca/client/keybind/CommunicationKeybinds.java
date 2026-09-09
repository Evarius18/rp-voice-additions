package com.evarius.rpvca.client.keybind;

import com.evarius.rpvca.client.ClientActions;
import com.evarius.rpvca.client.gui.RadioScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CommunicationKeybinds {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("rp-vca", "communication"));
    private static KeyMapping cycleSpeech;
    private static KeyMapping openPhone;
    private static KeyMapping openRadio;
    private static KeyMapping radioPtt;
    private static KeyMapping toggleSpeaker;
    private static KeyMapping answerCall;
    private static KeyMapping hangupCall;
    private static KeyMapping quietMode;
    private static KeyMapping whisperMode;
    private static KeyMapping normalMode;
    private static KeyMapping shoutMode;
    private static KeyMapping screamMode;
    private static boolean transmitting;

    private CommunicationKeybinds() {
    }

    public static void register() {
        cycleSpeech = bind("key.rp-vca.cycle_speech", GLFW.GLFW_KEY_GRAVE_ACCENT);
        openPhone = bind("key.rp-vca.open_phone", GLFW.GLFW_KEY_P);
        openRadio = bind("key.rp-vca.open_radio", GLFW.GLFW_KEY_R);
        radioPtt = bind("key.rp-vca.radio_ptt", GLFW.GLFW_KEY_LEFT_ALT);
        toggleSpeaker = bind("key.rp-vca.phone_speaker", GLFW.GLFW_KEY_UNKNOWN);
        answerCall = bind("key.rp-vca.phone_answer", GLFW.GLFW_KEY_UNKNOWN);
        hangupCall = bind("key.rp-vca.phone_hangup", GLFW.GLFW_KEY_UNKNOWN);
        quietMode = bind("key.rp-vca.mode_quiet", GLFW.GLFW_KEY_UNKNOWN);
        whisperMode = bind("key.rp-vca.mode_whisper", GLFW.GLFW_KEY_UNKNOWN);
        normalMode = bind("key.rp-vca.mode_normal", GLFW.GLFW_KEY_UNKNOWN);
        shoutMode = bind("key.rp-vca.mode_shout", GLFW.GLFW_KEY_UNKNOWN);
        screamMode = bind("key.rp-vca.mode_scream", GLFW.GLFW_KEY_UNKNOWN);
        ClientTickEvents.END_CLIENT_TICK.register(CommunicationKeybinds::tick);
    }

    private static KeyMapping bind(String id, int key) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(id, InputConstants.Type.KEYSYM, key, CATEGORY));
    }

    private static void tick(Minecraft client) {
        while (cycleSpeech.consumeClick()) ClientActions.send("speech_cycle");
        while (openPhone.consumeClick()) ClientActions.send("open_phone_request");
        while (openRadio.consumeClick()) client.setScreen(new RadioScreen());
        while (toggleSpeaker.consumeClick()) ClientActions.send("phone_speaker");
        while (answerCall.consumeClick()) ClientActions.send("phone_answer",
                com.evarius.rpvca.client.ClientCommunicationState.get().phoneCallId);
        while (hangupCall.consumeClick()) ClientActions.send("phone_hangup");
        while (quietMode.consumeClick()) ClientActions.send("speech_set", "quiet");
        while (whisperMode.consumeClick()) ClientActions.send("speech_set", "whisper");
        while (normalMode.consumeClick()) ClientActions.send("speech_set", "normal");
        while (shoutMode.consumeClick()) ClientActions.send("speech_set", "shout");
        while (screamMode.consumeClick()) ClientActions.send("speech_set", "scream");

        boolean pressed = radioPtt.isDown() && client.screen == null;
        if (pressed != transmitting) {
            transmitting = pressed;
            ClientActions.send("radio_tx", Boolean.toString(pressed));
        }
        if (client.player == null) {
            transmitting = false;
        }
    }
}
