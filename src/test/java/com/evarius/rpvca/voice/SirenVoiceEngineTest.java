package com.evarius.rpvca.voice;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SirenVoiceEngineTest {
    @Test
    void stripsId3v2MetadataBeforeNativeMp3Decoding() {
        byte[] source = new byte[] {
                'I', 'D', '3', 3, 0, 0,
                0, 0, 0, 4,
                1, 2, 3, 4,
                (byte) 0xFF, (byte) 0xFB, 5, 6
        };

        assertArrayEquals(new byte[] {(byte) 0xFF, (byte) 0xFB, 5, 6},
                SirenVoiceEngine.mp3AudioPayload(source));
    }

    @Test
    void keepsMp3WithoutId3v2MetadataUnchanged() {
        byte[] source = new byte[] {(byte) 0xFF, (byte) 0xFB, 5, 6};
        assertSame(source, SirenVoiceEngine.mp3AudioPayload(source));
    }

    @Test
    void stereoIsMixedToMono() {
        short[] result = SirenVoiceEngine.toMono48Khz(
                new short[]{1_000, 3_000, -2_000, 2_000},
                new AudioFormat(48_000, 16, 2, true, false));
        assertArrayEquals(new short[]{2_000, 0}, result);
    }

    @Test
    void lowerSampleRateIsResampledForVoiceChat() {
        short[] result = SirenVoiceEngine.toMono48Khz(
                new short[]{0, 1_000, 2_000, 3_000},
                new AudioFormat(24_000, 16, 1, true, false));
        assertEquals(8, result.length);
        assertEquals(0, result[0]);
        assertEquals(3_000, result[7]);
    }

    @Test
    void longSignalResamplingDoesNotOverflowIntegerArithmetic() {
        short[] source = new short[200_000]; // 100,000 stereo frames
        short[] result = SirenVoiceEngine.toMono48Khz(source,
                new AudioFormat(44_100, 16, 2, true, false));

        assertEquals(108_844, result.length);
    }

    @Test
    void signalGainReducesPcmAmplitudeWithoutMutatingCache() {
        short[] source = new short[] {20_000, -20_000, 1_000};
        short[] result = SirenVoiceEngine.applyGain(source, 0.25F);

        assertArrayEquals(new short[] {5_000, -5_000, 250}, result);
        assertArrayEquals(new short[] {20_000, -20_000, 1_000}, source);
    }
}
