
#ifndef HTTP_CLIENT_H
#define HTTP_CLIENT_H

#include <stddef.h>
#include <stdint.h>
#include "esp_err.h"

// 发送文本问题并获取 AI 回答
esp_err_t ai_send_message(const char *message,
                          char *reply,
                          size_t reply_size);

typedef void (*voice_status_callback_t)(const char *status);

esp_err_t ai_send_voice(
    const uint8_t *wav_data,
    size_t wav_size,
    char *reply,
    size_t reply_size,
    voice_status_callback_t status_callback
);

#endif
