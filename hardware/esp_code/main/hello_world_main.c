#include <stdio.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "driver/gpio.h"

#include "test.h"
#include "system.h"

void app_main(void)
{
    system_init();

    xTaskCreatePinnedToCore(key_task, "key_task", 4096, NULL, 3, NULL, 0);
    xTaskCreatePinnedToCore(waked_task, "waked_task", 16384, NULL, 5, NULL, 0);
    xTaskCreatePinnedToCore(audio_record_task, "audio_record_task", 16384, NULL, 4, NULL, 1);
}
