package com.example.wea_trav_assistant.voice;

/**
 * 音频校验：只接受 PCM WAV 16k/16bit/单声道。
 * 解析失败时给出明确原因，不静默放行。
 */
public final class AudioValidator {

    private AudioValidator() {}

    public static void validateWav(byte[] wav, long maxBytes, boolean strict) {
        if (wav == null || wav.length == 0) {
            throw new IllegalArgumentException("音频文件为空");
        }
        if (wav.length > maxBytes) {
            throw new IllegalArgumentException("音频文件超过大小限制");
        }
        if (!strict) return;
        if (wav.length < 44) {
            throw new IllegalArgumentException("不是有效的 WAV 文件");
        }
        String riff = new String(wav, 0, 4, java.nio.charset.StandardCharsets.US_ASCII);
        String wave = new String(wav, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
        if (!"RIFF".equals(riff) || !"WAVE".equals(wave)) {
            throw new IllegalArgumentException("不是有效的 WAV 文件");
        }
        int channels = readShortLE(wav, 22);
        int sampleRate = readIntLE(wav, 24);
        int bits = readShortLE(wav, 34);
        if (channels != 1) throw new IllegalArgumentException("仅支持单声道");
        if (sampleRate != 16000) throw new IllegalArgumentException("采样率必须为 16000 Hz");
        if (bits != 16) throw new IllegalArgumentException("位深必须为 16 bit");
    }

    private static int readShortLE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off+1] & 0xFF) << 8);
    }
    private static int readIntLE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off+1] & 0xFF) << 8)
                | ((b[off+2] & 0xFF) << 16) | ((b[off+3] & 0xFF) << 24);
    }
}