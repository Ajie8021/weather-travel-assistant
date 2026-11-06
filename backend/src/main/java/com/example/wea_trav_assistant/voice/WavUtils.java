package com.example.wea_trav_assistant.voice;

/** 最小 WAV 工具：生成/解析 44 字节 PCM WAV 头。 */
public final class WavUtils {

    private WavUtils() {}

    /** 把 PCM 数据包成 16k/16bit/单声道 WAV，便于 Mock 和格式转换测试 */
    public static byte[] wrapPcmAsWav(byte[] pcm) {
        int sampleRate = 16000, channels = 1, bits = 16;
        int byteRate = sampleRate * channels * bits / 8;
        int blockAlign = channels * bits / 8;
        int dataLen = pcm == null ? 0 : pcm.length;
        byte[] wav = new byte[44 + dataLen];

        writeAscii(wav, 0, "RIFF");
        writeIntLE(wav, 4, 36 + dataLen);
        writeAscii(wav, 8, "WAVE");
        writeAscii(wav, 12, "fmt ");
        writeIntLE(wav, 16, 16);
        writeShortLE(wav, 20, 1);          // PCM
        writeShortLE(wav, 22, channels);
        writeIntLE(wav, 24, sampleRate);
        writeIntLE(wav, 28, byteRate);
        writeShortLE(wav, 32, blockAlign);
        writeShortLE(wav, 34, bits);
        writeAscii(wav, 36, "data");
        writeIntLE(wav, 40, dataLen);
        if (dataLen > 0) System.arraycopy(pcm, 0, wav, 44, dataLen);
        return wav;
    }

    private static void writeAscii(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) b[off + i] = (byte) s.charAt(i);
    }
    private static void writeIntLE(byte[] b, int off, int v) {
        b[off] = (byte) v; b[off+1] = (byte)(v>>>8); b[off+2] = (byte)(v>>>16); b[off+3] = (byte)(v>>>24);
    }
    private static void writeShortLE(byte[] b, int off, int v) {
        b[off] = (byte) v; b[off+1] = (byte)(v>>>8);
    }
}