#include "mic_i2s.h"
#include "audio_config.h"

#include "driver/i2s_std.h"

#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include <math.h>
#include <stdint.h>
#include <limits.h>
#include <string.h>
#include "lcd.h"
#include "lcd_font.h"
#include "vad.h"
static const char *TAG = "MIC_I2S";

static i2s_chan_handle_t rx_handle = NULL;

// i2s 配置
esp_err_t mic_i2s_init(void)
{
    // 创建 I2S RX 通道
    // esp32 作为主机
    i2s_chan_config_t chan_cfg =
        I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_0, I2S_ROLE_MASTER);

    esp_err_t ret = i2s_new_channel(
        &chan_cfg,
        NULL,
        &rx_handle
    );

    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to create I2S RX channel");
        return ret;
    }

    i2s_std_slot_config_t slot_cfg =
        I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(
            I2S_DATA_BIT_WIDTH_32BIT,
            I2S_SLOT_MODE_MONO
        );

    slot_cfg.slot_mask = I2S_STD_SLOT_LEFT;
    // 配置 I2S 标准模式
    i2s_std_config_t std_cfg = {
        // 配置 I2S 时钟
        .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(MIC_SAMPLE_RATE),

        // 配置 I2S 数据格式和声道
        .slot_cfg = slot_cfg,

        .gpio_cfg = {
            .mclk = I2S_GPIO_UNUSED,
            .bclk = MIC_I2S_BCLK_GPIO,
            .ws   = MIC_I2S_WS_GPIO,
            .dout = I2S_GPIO_UNUSED,
            .din  = MIC_I2S_DIN_GPIO,
            .invert_flags = {
                .mclk_inv = false,
                .bclk_inv = false,
                .ws_inv   = false,
            },
        },
    };

    ret = i2s_channel_init_std_mode(rx_handle, &std_cfg);
    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to initialize I2S RX channel in standard mode");
        return ret;
    }

    return ESP_OK;
}

// 启动 I2S RX 通道
esp_err_t mic_i2s_start(void)
{
    esp_err_t ret = i2s_channel_enable(rx_handle);

    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to enable I2S RX");
        return ret;
    }

    ESP_LOGI(TAG, "I2S RX started");

    return ESP_OK;
}

// 读取麦克风数据
bool mic_i2s_read(int32_t *buffer,
                       size_t buffer_size,
                       size_t *mic_samples)
{
    size_t bytes_read = 0;
    *mic_samples = 0;
    esp_err_t ret =  i2s_channel_read(
        rx_handle,
        buffer,         // 读取的音频数据
        buffer_size,    // 读取的音频数据大小
        &bytes_read,     // 实际读取的字节数
        portMAX_DELAY   // 阻塞等待，直到读取完成
    );

    if(ret != ESP_OK || bytes_read <= 0)
        return false;
        
    *mic_samples = bytes_read / sizeof(int32_t);
    return true;
}

bool record_begin(int16_t *record_buffer, size_t *total_samples)
{
    // I2S 原始数据缓冲区
    int32_t mic_buffer[256];

    // VAD 30ms 输入帧
    int16_t vad_frame[VAD_FRAME_SAMPLES];
    size_t vad_frame_cnt = 0;

    // 保存开始录音前的3帧数据
    int16_t pre_buffer[AUDIO_START_VOICE_FRAMES][VAD_FRAME_SAMPLES];
    size_t pre_index = 0;

    // 录音统计
    size_t mic_samples = 0;
    uint32_t wait_frames = 0;
    uint32_t voice_candidate_frames = 0;
    uint32_t silence_frames = 0;
    uint32_t voice_frames = 0;

    // 最大录音采样点数
    const size_t max_samples = AUDIO_MAX_RECORD_SAMPLES;

    *total_samples = 0;

    record_state_t state = RECORD_WAIT_VOICE;

    while (state != RECORD_FINISHED)
    {
        // 读取麦克风数据
        if (!mic_i2s_read(mic_buffer, sizeof(mic_buffer), &mic_samples))
        {
            ESP_LOGE("record", "麦克风读取失败");
            break;
        }

        for (size_t i = 0; i < mic_samples; i++)
        {
            // INMP441 32位数据转换为16位PCM
            int16_t pcm = (int16_t)(mic_buffer[i] >> 16);

            vad_frame[vad_frame_cnt++] = pcm;

            // 不足480个采样点，继续累积
            if (vad_frame_cnt < VAD_FRAME_SAMPLES)
                continue;

            vad_frame_cnt = 0;

            // VAD语音检测
            bool is_speech = vad_detect(vad_frame);

            switch (state)
            {
                // ================= 等待用户说话 =================
                case RECORD_WAIT_VOICE:
                {
                    wait_frames++;

                    // 保存最近3帧，形成循环缓冲区
                    memcpy(
                        pre_buffer[pre_index],
                        vad_frame,
                        sizeof(vad_frame)
                    );

                    pre_index = (pre_index + 1) % AUDIO_START_VOICE_FRAMES;

                    if (is_speech)
                    {
                        voice_candidate_frames++;
                    }
                    else
                    {
                        voice_candidate_frames = 0;
                    }

                    // 连续3帧检测到语音，确认开始录音
                    if (voice_candidate_frames >= AUDIO_START_VOICE_FRAMES)
                    {
                        ESP_LOGI("record", "检测到有效语音，开始录音");

                        lcd_clear(BLACK);
                        lcd_show_text(0, 80, "说话中...", GREEN, BLACK);

                        // 按时间顺序，将最近3帧补入录音缓冲区
                        for (size_t f = 0; f < AUDIO_START_VOICE_FRAMES; f++)
                        {
                            size_t index =
                                (pre_index + f) % AUDIO_START_VOICE_FRAMES;

                            memcpy(
                                &record_buffer[*total_samples],
                                pre_buffer[index],
                                sizeof(pre_buffer[index])
                            );

                            *total_samples += VAD_FRAME_SAMPLES;
                        }

                        voice_frames = AUDIO_START_VOICE_FRAMES;
                        silence_frames = 0;

                        state = RECORD_RECORDING;
                    }

                    // 等待语音超时
                    if (state == RECORD_WAIT_VOICE &&
                        wait_frames >= AUDIO_WAIT_TIMEOUT_FRAMES)
                    {
                        ESP_LOGI("record", "等待用户说话超时");
                        state = RECORD_FINISHED;
                    }

                    break;
                }

                // ================= 正式录音 =================
                case RECORD_RECORDING:
                {
                    // 计算剩余缓冲区容量
                    size_t remaining = max_samples - *total_samples;

                    // 本帧实际写入的采样点数
                    size_t copy_samples =
                        remaining < VAD_FRAME_SAMPLES ?
                        remaining : VAD_FRAME_SAMPLES;

                    // 保存当前帧PCM数据
                    memcpy(
                        &record_buffer[*total_samples],
                        vad_frame,
                        copy_samples * sizeof(int16_t)
                    );

                    *total_samples += copy_samples;

                    // 统计语音帧和静音帧
                    if (is_speech)
                    {
                        voice_frames++;
                        silence_frames = 0;
                    }
                    else
                    {
                        silence_frames++;
                    }

                    // 达到最大录音时长
                    if (*total_samples >= max_samples)
                    {
                        ESP_LOGI("record", "达到最大录音时长");
                        state = RECORD_FINISHED;
                        break;
                    }

                    // 连续静音达到800ms
                    if (silence_frames >= AUDIO_SILENCE_FRAMES)
                    {
                        // 语音不足300ms，判定为误触发
                        if (voice_frames < AUDIO_MIN_VOICE_FRAMES)
                        {
                            ESP_LOGI("record", "语音时长不足300ms，重新等待");

                            *total_samples = 0;
                            voice_frames = 0;
                            silence_frames = 0;
                            voice_candidate_frames = 0;
                            pre_index = 0;

                            state = RECORD_WAIT_VOICE;
                        }
                        else
                        {
                            ESP_LOGI("record", "检测到连续静音，录音结束");

                            lcd_clear(BLACK);
                            lcd_show_text(0, 80, "问题结束", GREEN, BLACK);

                            state = RECORD_FINISHED;
                        }
                    }

                    break;
                }

                default:
                    state = RECORD_FINISHED;
                    break;
            }

            if (state == RECORD_FINISHED)
                break;
        }
    }

    ESP_LOGI("record", "录音结束");
    ESP_LOGI("record", "样本数量: %u", (unsigned int)*total_samples);
    ESP_LOGI("record", "录音数据大小: %u bytes",
             (unsigned int)(*total_samples * sizeof(int16_t)));
    ESP_LOGI("record", "录音时长: %.2f s",
             (float)*total_samples / MIC_SAMPLE_RATE);
    ESP_LOGI("record", "有效语音帧数: %u", (unsigned int)voice_frames);

    // 只有存在有效录音数据才返回成功
    return *total_samples > 0;
}
