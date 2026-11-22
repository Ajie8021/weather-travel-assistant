#ifndef WAV_H
#define WAV_H

#include <stdint.h>
#include <stddef.h>
#include <stdbool.h>

#define WAV_HEADER_SIZE       44
#define WAV_SAMPLE_RATE       16000
#define WAV_CHANNELS          1
#define WAV_BITS_PER_SAMPLE   16

/**
 * @brief 将 PCM 数据封装成 WAV 格式
 *
 * @param pcm             原始 PCM 数据
 * @param total_samples   PCM 采样点数量
 * @param wav_buffer      输出 WAV 缓冲区
 * @param buffer_size     输出缓冲区容量
 * @param wav_size        实际生成的 WAV 数据长度
 *
 * @return true 成功，false 失败
 */
bool wav_pack_pcm(
    const int16_t *pcm,
    size_t total_samples,
    uint8_t *wav_buffer,
    size_t buffer_size,
    size_t *wav_size
);

#endif
