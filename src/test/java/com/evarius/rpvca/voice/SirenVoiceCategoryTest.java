package com.evarius.rpvca.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SirenVoiceCategoryTest {
    @Test
    void categoryNameFitsSimpleVoiceChatPacketLimit() {
        assertTrue(SirenVoiceEngine.CATEGORY_NAME.length() <= 16,
                "Simple Voice Chat accepts at most 16 characters for a volume category name");
    }
}
