package com.example.wea_trav_assistant.voice;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 音频校验：合法 WAV 通过，非法头/声道/采样率/位深/大小被拒。 */
class AudioValidatorTest {

    private byte[] validWav() {
        return WavUtils.wrapPcmAsWav(new byte[]{1, 2, 3, 4});
    }

    @Test
    void validWav_passes() {
        assertDoesNotThrow(() -> AudioValidator.validateWav(validWav(), 1024 * 1024, true));
    }

    @Test
    void empty_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> AudioValidator.validateWav(new byte[0], 1024, true));
    }

    @Test
    void tooLarge_rejected() {
        byte[] wav = validWav();
        assertThrows(IllegalArgumentException.class,
                () -> AudioValidator.validateWav(wav, 10, true));
    }

    @Test
    void notRiff_rejected() {
        byte[] bad = new byte[44];
        assertThrows(IllegalArgumentException.class,
                () -> AudioValidator.validateWav(bad, 1024, true));
    }

    @Test
    void nonStrict_skipsHeaderCheck() {
        byte[] bad = new byte[10];
        assertDoesNotThrow(() -> AudioValidator.validateWav(bad, 1024, false));
    }
}