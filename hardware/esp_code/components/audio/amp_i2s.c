#include "amp_i2s.h"
#include "audio_config.h"

#include "driver/i2s_std.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "math.h"

#define SAMPLE_RATE 16000
#define PI 3.14159265358979323846

static const char *TAG = "AMP_I2S";
static i2s_chan_handle_t tx_handle = NULL;
/**
 * @brief 初始化 MAX98357A I2S TX
 */
esp_err_t amp_i2s_init(void)
{
    // 1. 创建 I2S TX 通道
    i2s_chan_config_t chan_cfg =
        I2S_CHANNEL_DEFAULT_CONFIG(I2S_NUM_1, I2S_ROLE_MASTER);

    esp_err_t ret = i2s_new_channel(
        &chan_cfg,
        &tx_handle,
        NULL
    );

    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to create I2S TX channel");
        return ret;
    }

    // 2. 配置 I2S 标准模式
    i2s_std_config_t std_cfg = {
        .clk_cfg = I2S_STD_CLK_DEFAULT_CONFIG(AMP_SAMPLE_RATE),

        .slot_cfg = I2S_STD_PHILIPS_SLOT_DEFAULT_CONFIG(
            I2S_DATA_BIT_WIDTH_16BIT,
            I2S_SLOT_MODE_STEREO
        ),

        .gpio_cfg = {
            .mclk = I2S_GPIO_UNUSED,
            .bclk = AMP_I2S_BCLK_GPIO,
            .ws   = AMP_I2S_WS_GPIO,
            .dout = AMP_I2S_DOUT_GPIO,
            .din  = I2S_GPIO_UNUSED,
        },
    };

    // 3. 初始化标准模式
    ret = i2s_channel_init_std_mode(tx_handle, &std_cfg);

    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to initialize I2S TX");
        return ret;
    }

    ESP_LOGI(TAG, "I2S TX initialized");

    return ESP_OK;
}


/**
 * @brief 启动 I2S TX
 */
esp_err_t amp_i2s_start(void)
{
    esp_err_t ret = i2s_channel_enable(tx_handle);

    if (ret != ESP_OK)
    {
        ESP_LOGE(TAG, "Failed to enable I2S TX");
        return ret;
    }

    ESP_LOGI(TAG, "I2S TX started");

    return ESP_OK;
}

/**
 * @brief 向 MAX98357A 发送 PCM 数据
 */
esp_err_t amp_i2s_write(
    const int16_t *buffer,
    size_t buffer_size,
    size_t *bytes_written)
{
    return i2s_channel_write(
        tx_handle,
        buffer,
        buffer_size,
        bytes_written,
        portMAX_DELAY
    );
}

void generate_sine(int16_t *buffer,
                          int samples,
                          float frequency)
{
    static float phase = 0.0f;

    for (int i = 0; i < samples; i++)
    {
        int16_t sample = (int16_t)(
            3000.0f * sinf(phase)
        );

        // 左右声道相同
        buffer[2 * i]     = sample;
        buffer[2 * i + 1] = sample;

        phase += 2.0f * PI * frequency / SAMPLE_RATE;

        if (phase >= 2.0f * PI)
            phase -= 2.0f * PI;
    }
}

void amp_play_pcm(const int16_t *pcm, size_t samples)
{
    amp_i2s_start();

    int16_t stereo_buf[256 * 2];
    size_t offset = 0;

    while (offset < samples)
    {
        size_t chunk = samples - offset;

        if (chunk > 256)
            chunk = 256;

        // 单声道复制到左右声道
        for (size_t i = 0; i < chunk; i++)
        {
            stereo_buf[i * 2]     = pcm[offset + i];
            stereo_buf[i * 2 + 1] = pcm[offset + i];
        }

        size_t bytes_written = 0;
        size_t total_bytes = chunk * 2 * sizeof(int16_t);
        size_t written_offset = 0;

        while (written_offset < total_bytes)
        {
            esp_err_t ret = i2s_channel_write(
                tx_handle,
                (uint8_t *)stereo_buf + written_offset,
                total_bytes - written_offset,
                &bytes_written,
                portMAX_DELAY
            );

            if (ret != ESP_OK || bytes_written == 0)
            {
                ESP_LOGE("AMP", "PCM playback failed");
                return;
            }

            written_offset += bytes_written;
        }

        offset += chunk;
    }

    // 等待播放数据传输完成
    i2s_channel_disable(tx_handle);

    ESP_LOGI("AMP", "I2S TX stopped");
}
