#ifndef AUDIO_CONFIG_H
#define AUDIO_CONFIG_H

/* ================= INMP441 麦克风引脚配置 ================= */

// I2S RX
#define MIC_I2S_BCLK_GPIO    GPIO_NUM_5
#define MIC_I2S_WS_GPIO      GPIO_NUM_4
#define MIC_I2S_DIN_GPIO     GPIO_NUM_6

/* ================= INMP441 音频参数 ================= */

#define MIC_SAMPLE_RATE      16000      //采样率
#define MIC_BITS_PER_SAMPLE  32         //位宽
#define MIC_CHANNEL_NUM      1          //单通道

/* ================= 音频缓冲区配置 ================= */

#define MIC_READ_BUF_SIZE    1024

/* ================= MAX98357A 功放引脚配置 ================= */

// I2S TX
#define AMP_I2S_BCLK_GPIO    GPIO_NUM_15
#define AMP_I2S_WS_GPIO      GPIO_NUM_16
#define AMP_I2S_DOUT_GPIO    GPIO_NUM_7

/* ================= 扬声器音频参数 ================= */

#define AMP_SAMPLE_RATE      16000
#define AMP_BITS_PER_SAMPLE  16
#define AMP_CHANNEL_NUM      1

/* ================== 录音配置 ==================== */

#define AUDIO_MAX_RECORD_TIME_MS    8000    // 单次最大录音时长
#define AUDIO_WAIT_TIMEOUT_MS       8000    // 等待用户说话超时时间
#define AUDIO_SILENCE_TIME_MS       800     // 连续静音结束时间
#define AUDIO_MIN_VOICE_TIME_MS     300     // 最短有效语音时长

/* ================== VAD 配置 ==================== */

#define VAD_SAMPLE_RATE             MIC_SAMPLE_RATE
#define VAD_FRAME_LENGTH_MS         30

// 16000Hz × 30ms = 480 个采样点
#define VAD_FRAME_SAMPLES \
    (VAD_SAMPLE_RATE * VAD_FRAME_LENGTH_MS / 1000)

// 连续5帧语音，确认开始录音
#define AUDIO_START_VOICE_FRAMES    5

// 最短有效语音帧数：300ms / 30ms = 10帧
#define AUDIO_MIN_VOICE_FRAMES \
    ((AUDIO_MIN_VOICE_TIME_MS + VAD_FRAME_LENGTH_MS - 1) / VAD_FRAME_LENGTH_MS)

// 静音结束所需帧数：800ms / 30ms，向上取整为27帧
#define AUDIO_SILENCE_FRAMES \
    ((AUDIO_SILENCE_TIME_MS + VAD_FRAME_LENGTH_MS - 1) / VAD_FRAME_LENGTH_MS)

// 等待超时对应的帧数
#define AUDIO_WAIT_TIMEOUT_FRAMES \
    ((AUDIO_WAIT_TIMEOUT_MS + VAD_FRAME_LENGTH_MS - 1) / VAD_FRAME_LENGTH_MS)

// 最大录音采样点数
#define AUDIO_MAX_RECORD_SAMPLES \
    (MIC_SAMPLE_RATE * AUDIO_MAX_RECORD_TIME_MS / 1000)

// 最大录音缓冲区字节数
#define AUDIO_MAX_RECORD_BYTES \
    (AUDIO_MAX_RECORD_SAMPLES * sizeof(int16_t))

#endif
