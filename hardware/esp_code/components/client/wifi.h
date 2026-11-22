#ifndef _WIFI_H_
#define _WIFI_H_

#include "esp_err.h"
#include "freertos/FreeRTOS.h"
#include "freertos/event_groups.h"
#include "stdint.h"

#define DEFAULT_SSID   "esp"
#define DEFAULT_PWD    "00054188"

esp_err_t wifi_init(EventGroupHandle_t g_event_group);
esp_err_t wifi_wait_connected(uint32_t timeout_ms);

#endif
