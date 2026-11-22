#ifndef AMP_I2S_H
#define AMP_I2S_H

#include <stdint.h>
#include <stddef.h>
#include "esp_err.h"

esp_err_t amp_i2s_init(void);
esp_err_t amp_i2s_start(void);
esp_err_t amp_i2s_write(
    const int16_t *buffer,
    size_t buffer_size,
    size_t *bytes_written
);

void generate_sine(int16_t *buffer,
                          int samples,
                          float frequency);
                          
void amp_play_pcm(const int16_t *pcm, size_t samples);
#endif
