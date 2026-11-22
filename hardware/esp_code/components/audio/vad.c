#include "vad.h"
#include "audio_config.h"
#include "esp_vad.h"
#include "stddef.h"
#include "esp_log.h"

static vad_handle_t vad_handle = NULL;

esp_err_t myvad_init(void)
{
    vad_handle = vad_create(VAD_MODE_1);

    if (vad_handle == NULL)
    {
        ESP_LOGE("VAD", "VAD 初始化失败");
        return -1;
    }

    ESP_LOGI("VAD", "VAD 初始化成功");
    ESP_LOGI("VAD", "vad_handle = %p", vad_handle);
    return ESP_OK;
}

//检测
bool vad_detect(int16_t *pcm_frame)
{
    if (vad_handle == NULL || pcm_frame == NULL)
    {
        ESP_LOGE("VAD", "VAD handle 或输入数据为空");
        return false;
    }
            
    vad_state_t state = vad_process(
        vad_handle,
        pcm_frame,
        SAMPLE_RATE_HZ,
        VAD_FRAME_LENGTH_MS
    );

    return state == VAD_SPEECH;
}

// 释放
void vad_deinit(void)
{
    if (vad_handle != NULL)
    {
        vad_destroy(vad_handle);
        vad_handle = NULL;
    }
}
