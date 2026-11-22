#ifndef WAKENET_H
#define WAKENET_H

#include "esp_err.h"
#include <stdint.h>
#include <stdbool.h>

esp_err_t wakenet_init(void);
bool wakenet_detect(int16_t *pcm_buf);

#endif
