#ifndef _VAD_H_
#define _VAD_H_

#include "esp_err.h"
#include "stdbool.h"

esp_err_t myvad_init(void);
bool vad_detect(int16_t *pcm_frame);

#endif
