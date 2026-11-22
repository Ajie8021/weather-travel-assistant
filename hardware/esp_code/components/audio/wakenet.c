#include "esp_wn_iface.h"
#include "esp_wn_models.h"
#include "wakenet.h"
#include "esp_log.h"
#include "model_path.h"
#include "esp_heap_caps.h"

static const char *TAG = "WAKENET";

static const esp_wn_iface_t *wakenet = NULL;
static model_iface_data_t *model_data = NULL;

esp_err_t wakenet_init(void)
{
    const char *model_name = "wn9_nihaoxiaozhi_tts";

    // 1. 加载 Flash 中的语音模型
    srmodel_list_t *models = esp_srmodel_init("model");

    if (models == NULL) {
        ESP_LOGE(TAG, "Failed to initialize SR models");
        return ESP_FAIL;
    }

    // 2. 获取 WakeNet 接口
    wakenet = esp_wn_handle_from_name(model_name);

    if (wakenet == NULL) {
        ESP_LOGE(TAG, "Failed to get WakeNet interface");
        return ESP_FAIL;
    }

    // 3. 创建 WakeNet 实例
    model_data = wakenet->create(model_name, DET_MODE_90);

    if (model_data == NULL) {
        ESP_LOGE(TAG, "Failed to create WakeNet model");
        return ESP_FAIL;
    }

    ESP_LOGI(TAG, "WakeNet model created");

    ESP_LOGI(TAG, "Sample rate: %d",
             wakenet->get_samp_rate(model_data));
    ESP_LOGI(TAG, "Chunk size: %d",
             wakenet->get_samp_chunksize(model_data));

    return ESP_OK;
}

bool wakenet_detect(int16_t *pcm_buf)
{ 
    wakenet_state_t state = wakenet->detect(model_data, pcm_buf);
    if (state == WAKENET_DETECTED)
    {
        ESP_LOGI(TAG, "唤醒成功：你好小智！");
        return true;
    }
    return false;
}
