#include "system.h"

#include "nvs_flash.h"
#include "http_client.h"
#include "esp_log.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include <stdlib.h>
#include "esp_heap_caps.h"

#include "wifi.h"
// #include "ble.h"
#include "lcd.h"
#include "lcd_font.h"
#include "mic_i2s.h"
#include "amp_i2s.h"
#include "wakenet.h"
#include "vad.h"
#include "wav.h"
#include "button.h"
#include "audio_config.h"

#define WIFI_CONNECT_TIMEOUT_MS 10000
#define AUDIO_WAKEUP_BIT    BIT2
#define AUDIO_RECORD_DONE_BIT BIT3

static EventGroupHandle_t event_group = NULL;
static QueueHandle_t xbleQueue;
static SemaphoreHandle_t xSpeekSemphore = NULL; 
static char ai_reply[2048];
static char display_text[2048];

void system_init(void)
{
     // 创建事件组
    event_group = xEventGroupCreate();
    xSpeekSemphore = xSemaphoreCreateBinary();
    // xbleQueue = xQueueCreate( 5, BLE_DATA_MAX_LEN);

    if (event_group == NULL || xSpeekSemphore == NULL)
    {
        ESP_LOGW("INIT", "Failed to create event group or queue");
        return ;
    }

    lcd_init();
    button_init();

    esp_err_t ret = nvs_flash_init();

    if (ret == ESP_ERR_NVS_NO_FREE_PAGES ||
        ret == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_ERROR_CHECK(nvs_flash_erase());
        ret = nvs_flash_init();
    }

    ESP_ERROR_CHECK(ret);
    // ble_init(xbleQueue);
    // 初始化 Wi-Fi，等待连接
    ESP_ERROR_CHECK(wifi_init(event_group));
    // 最多等待 15 秒
    esp_err_t err = wifi_wait_connected(WIFI_CONNECT_TIMEOUT_MS);

    if (err != ESP_OK)
    {
        ESP_LOGE("INIT", "WiFi connection timeout");
        return;
    }
    
    ESP_ERROR_CHECK(wakenet_init());
    ESP_ERROR_CHECK(myvad_init());
    ESP_ERROR_CHECK(mic_i2s_init());
    ESP_ERROR_CHECK(mic_i2s_start());
    ESP_ERROR_CHECK(amp_i2s_init());
    lcd_clear(BLACK);
    lcd_show_string(1, 1, "weather assist",
                    GREEN, BLACK);
    
    lcd_set_font_size(36);
}

// 按键控制任务启动
void key_task(void *arg)
{
    uint8_t last_key = UNPRESSED;
    uint8_t now_key = UNPRESSED;
    while(1)
    {
        now_key = get_button_state(KEY_PIN);
        if(now_key != last_key)
        {
            vTaskDelay(pdMS_TO_TICKS(20)); //消抖
            now_key = get_button_state(KEY_PIN);
            if(now_key != last_key)
            {
                if(now_key == PRESSED) //检测到按下
                {
                    xSemaphoreGive(xSpeekSemphore); // 发事件通知
                }
                last_key = now_key;
            }
        }
        vTaskDelay(pdMS_TO_TICKS(20));
    }
}

void waked_task(void *arg)
{
    int32_t mic_buffer[256];
    size_t samples = 0;
    int16_t pcm_buffer[512];

    int pcm_cnt = 0;
    while (1)
    {
        if(!mic_i2s_read( mic_buffer, sizeof(mic_buffer), &samples)) 
        {
            vTaskDelay(1);
            continue;
        }
        for (size_t i = 0; i < samples; i++)
        {
            pcm_buffer[pcm_cnt++] = (int16_t)(mic_buffer[i] >> 16);
            if (pcm_cnt >= 512)
            {
                pcm_cnt = 0;
                // 等待唤醒关键词
                if(wakenet_detect(pcm_buffer))
                { 
                    ESP_LOGI("detect","检测到关键词");
                    // 通知录音任务
                    xEventGroupSetBits(event_group,AUDIO_WAKEUP_BIT);

                     // 等待录音任务完成
                    xEventGroupWaitBits(
                        event_group,
                        AUDIO_RECORD_DONE_BIT,
                        pdTRUE,
                        pdFALSE,
                        portMAX_DELAY
                    );

                    ESP_LOGI("detect", "录音结束，恢复唤醒检测");
                }
            }
        }
    }
}

static void voice_status_display(const char *status)
{
    lcd_fill(0, 80, 239, 150, BLACK);
    lcd_show_text(0, 80, status, GREEN, BLACK);
}

void audio_record_task(void *arg)
{
    size_t total_samples = 0;
    while (1)
    {
        EventBits_t bits = xEventGroupWaitBits(
            event_group,
            AUDIO_WAKEUP_BIT,
            pdTRUE,       // 读取后清除事件位
            pdFALSE,      // 等待任意一个事件位
            portMAX_DELAY
        );
        if (!(bits & AUDIO_WAKEUP_BIT))
            continue;
            
        ESP_LOGI("record", "收到唤醒通知，等待按键确认");

        lcd_clear(BLACK);
        lcd_show_text(0, 80, "小智已唤醒，请按键",
                      GREEN, BLACK);
        
        // 在 PSRAM 中申请最大录音空间
        int16_t *record_buffer = heap_caps_malloc(
            AUDIO_MAX_RECORD_BYTES,
            MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT
        );

        if (record_buffer == NULL)
        {
            ESP_LOGE("record", "录音缓冲区申请失败");

            xEventGroupSetBits(
                event_group,
                AUDIO_RECORD_DONE_BIT
            );
            continue;
        }
        // 连续对话循环
        while(1)
        {
            lcd_show_text(0, 80, "请按键开始说话",
                GREEN, BLACK);
            // 等待按键按下
            xSemaphoreTake(xSpeekSemphore, portMAX_DELAY);

            ESP_LOGI("record", "按键确认，开始检测人声");

            lcd_show_text(0, 80, "请开始说话",
                        GREEN, BLACK);

            // 录音
            bool ret = record_begin(record_buffer, &total_samples);
            // 没有检测到有效语音，退出连续对话
            if (!ret)
            {
                ESP_LOGI("record", "等待超时或录音失败，退出对话");
                break;
            }
            // 有效录音才进行后续处理
            if (total_samples > 0) {
                ESP_LOGI("record", "录音完成，开始处理");

                // 1. 计算 WAV 缓冲区大小
                size_t wav_buffer_size =
                    WAV_HEADER_SIZE + total_samples * sizeof(int16_t);

                // 2. 申请 WAV 缓冲区
                uint8_t *wav_buffer = heap_caps_malloc(
                    wav_buffer_size,
                    MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT
                );

                if (wav_buffer == NULL) {
                    ESP_LOGE("record", "WAV 缓冲区申请失败");
                } else {
                    // 3. PCM 封装成 WAV
                    size_t wav_size = 0;

                    bool wav_ret = wav_pack_pcm(
                        record_buffer,
                        total_samples,
                        wav_buffer,
                        wav_buffer_size,
                        &wav_size
                    );

                    if (wav_ret) {
                        ESP_LOGI("record", "WAV 封装成功，准备上传");

                        // 4. 上传语音
                        char ai_reply[2048] = {0};

                        esp_err_t ret = ai_send_voice(
                            wav_buffer,
                            wav_size,
                            ai_reply,
                            sizeof(ai_reply),
                            voice_status_display
                        );

                        if (ret == ESP_OK)
                        {
                            ESP_LOGI("record", "服务器回答: %s", ai_reply);

                            lcd_show_text(0, 50, ai_reply, GREEN, BLACK);
                        }
                        else
                        {
                            ESP_LOGE("record", "语音请求失败: %s",
                                    esp_err_to_name(ret));

                            lcd_show_text(0, 80, "请求失败，请重试", GREEN, BLACK);
                        }

                        if (ret == ESP_OK) {
                            ESP_LOGI("record", "语音请求成功");
                        } else {
                            ESP_LOGE("record", "语音请求失败: %s",
                                    esp_err_to_name(ret));
                        }
                    } else {
                        ESP_LOGE("record", "WAV 封装失败");
                    }

                    // 5. 释放 WAV 缓冲区
                    free(wav_buffer);
                }

                // 暂时保留本地录音回放，方便验证
                // amp_play_pcm(record_buffer, total_samples);

                ESP_LOGI("record", "录音处理完成");
            }
        }

        free(record_buffer);

        xEventGroupSetBits(event_group, AUDIO_RECORD_DONE_BIT);
        ESP_LOGI("record", "录音结束");
        lcd_fill(0,0,239,40,BLACK);
        lcd_show_text(0, 0, "请重新唤醒小智",
                    GREEN, BLACK);
    }
}

void http_task(void *arg)
{
    char ble_cmd[BLE_DATA_MAX_LEN] = {0};
    lcd_clear(BLACK);
    lcd_show_string(1, 1, "weather assist",
                    GREEN, BLACK);
    
    lcd_set_font_size(16);
    while(1){
        // 等待 BLE 指令
        if (xQueueReceive(xbleQueue, ble_cmd, portMAX_DELAY) == pdTRUE)
        {
            ESP_LOGI("HTTP_TASK", "Received BLE command: %s", ble_cmd);
            // 显示用户指令和等待状态
            lcd_fill(0, 40, 239, 239, BLACK);
            lcd_show_text(0, 45, ble_cmd, WHITE, BLACK);
            lcd_show_string(4, 1, "Thinking...", WHITE, BLACK);

            // 发送 HTTP 请求
            esp_err_t ret = ai_send_message(
                ble_cmd,
                ai_reply,
                sizeof(ai_reply)
            );

            if (ret == ESP_OK)
            {
                text_filter(ai_reply, display_text, sizeof(display_text));
                lcd_fill(0, 70, 239, 239, BLACK);
                lcd_show_text(0, 60, display_text, WHITE, BLACK);
            }
            else
            {
                lcd_fill(0, 90, 239, 239, BLACK);
                lcd_show_string(4, 1, "Request Failed",
                                RED, BLACK);
            }
        }
    }
}
