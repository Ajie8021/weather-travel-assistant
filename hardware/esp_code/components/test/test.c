#include "test.h"
#include <stdlib.h>
#include <math.h>
#include "stdint.h"
#include <stddef.h>
#include "esp_err.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"

#include "mic_i2s.h"
#include "amp_i2s.h"

static const char *TAG = "MIC_TEST";

static bool flag = true;
// 测试麦克风采集数据任务
void mic_test_task(void *arg)
{
    int32_t mic_buffer[256];
    size_t bytes_read = 0;

    int64_t sum_abs = 0;
    int64_t sum_squares = 0;

    int32_t max = INT32_MIN;
    int32_t min = INT32_MAX;
    int32_t peak = 0;

    int samples_total = 0;

    TickType_t start_tick = xTaskGetTickCount();

    while (1)
    {
        esp_err_t ret = mic_i2s_read(
            mic_buffer,
            sizeof(mic_buffer),
            &bytes_read
        );

        if (ret == ESP_OK && bytes_read > 0)
        {
            int samples = bytes_read / sizeof(int32_t);

            for (int i = 0; i < samples; i++)
            {
                if(i <= 20 && flag == true)
                {
                    ESP_LOGI(TAG, "RAW[%d] = 0x%08lX (%ld)",
                    i,
                    (unsigned long)mic_buffer[i],
                    (long)mic_buffer[i]);
                    if(i == 20)
                        flag = false;
                }
                int32_t value = mic_buffer[i] >> 8;

                int64_t abs_value = (value == INT32_MIN)
                                  ? INT32_MAX
                                  : llabs((int64_t)value);

                if (value > max) max = value;
                if (value < min) min = value;
                if (abs_value > peak) peak = (int32_t)abs_value;

                sum_abs += abs_value;
                sum_squares += (int64_t)value * value;
            }

            samples_total += samples;
        }

        if ((xTaskGetTickCount() - start_tick) >= pdMS_TO_TICKS(5000))
        {
            break;
        }
    }

    if (samples_total > 0)
    {
        float average = (float)sum_abs / samples_total;
        float rms = sqrtf((double)sum_squares / samples_total);

        ESP_LOGI(TAG, "Samples: %d", samples_total);
        ESP_LOGI(TAG, "Max: %ld", (long)max);
        ESP_LOGI(TAG, "Min: %ld", (long)min);
        ESP_LOGI(TAG, "Peak: %ld", (long)peak);
        ESP_LOGI(TAG, "Average Abs: %.2f", average);
        ESP_LOGI(TAG, "RMS: %.2f", rms);
    }

    ESP_LOGI(TAG, "Microphone test completed");

    vTaskDelete(NULL);
}

// 测试功放播放正弦波任务
void amp_test_task(void *arg)
{
    int16_t buffer[256 * 2];
    size_t bytes_written = 0;

    while (1)
    {
        generate_sine(buffer, 256, 440.0f);

        esp_err_t ret = amp_i2s_write(
            buffer,
            sizeof(buffer),
            &bytes_written
        );

        if (ret != ESP_OK)
        {
            ESP_LOGE(TAG, "I2S write failed: %s",
                     esp_err_to_name(ret));
        }
    }
}
