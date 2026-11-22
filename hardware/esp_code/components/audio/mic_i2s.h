#ifndef MIC_I2S_H
#define MIC_I2S_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>
#include "esp_err.h"

typedef enum
{
    RECORD_WAIT_VOICE = 0,  // 等待有效语音
    RECORD_RECORDING,       // 正式录音
    RECORD_FINISHED         // 录音结束
} record_state_t;

esp_err_t mic_i2s_init(void);
esp_err_t mic_i2s_start(void);

bool mic_i2s_read(int32_t *buffer,
                       size_t buffer_size,
                       size_t *samples);

bool record_begin(int16_t *record_buffer, size_t *total_samples);

#endif
