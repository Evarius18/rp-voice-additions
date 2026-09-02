package com.evarius.rpvca.client.keybind;

import com.evarius.rpvca.client.ClientActions;
import com.evarius.rpvca.client.gui.RadioScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CommunicationKeybinds {
    private static final KeyBinding.Category CATEGORY = KeyBinding.Category.create(
            Identifier.of("rp-vca", "communication"));
    private static KeyBinding cycleSpeech;
    private static KeyBinding openPhone;
    private static KeyBinding openRadio;
    private static KeyBinding radioPtt;
    private static KeyBinding toggleSpeaker;
    private static KeyBinding answerCall;
    private static KeyBinding hangupCall;
    private static KeyBinding quietMode;
    private static KeyBinding whisperMode;
    private static KeyBinding normalMode;
    private static KeyBinding shoutMode;
    private static KeyBinding screamMode;
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

    private static KeyBinding bind(String id, int key) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, key, CATEGORY));
    }

    private static void tick(MinecraftClient client) {
        while (cycleSpeech.wasPressed()) ClientActions.send("speech_cycle");
        while (openPhone.wasPressed()) ClientActions.send("open_phone_request");
        while (openRadio.wasPressed()) client.setScreen(new RadioScreen());
        while (toggleSpeaker.wasPressed()) ClientActions.send("phone_speaker");
        while (answerCall.wasPressed()) ClientActions.send("phone_answer",
                com.evarius.rpvca.client.ClientCommunicationState.get().phoneCallId);
        while (hangupCall.wasPressed()) ClientActions.send("phone_hangup");
        while (quietMode.wasPressed()) ClientActions.send("speech_set", "quiet");
        while (whisperMode.wasPressed()) ClientActions.send("speech_set", "whisper");
        while (normalMode.wasPressed()) ClientActions.send("speech_set", "normal");
        while (shoutMode.wasPressed()) ClientActions.send("speech_set", "shout");
        while (screamMode.wasPressed()) ClientActions.send("speech_set", "scream");

        boolean pressed = radioPtt.isPressed() && client.currentScreen == null;
        if (pressed != transmitting) {
            transmitting = pressed;
            ClientActions.send("radio_tx", Boolean.toString(pressed));
        }
        if (client.player == null) {
            transmitting = false;
        }
    }
}
