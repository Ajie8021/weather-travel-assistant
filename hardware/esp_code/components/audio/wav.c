#include "wav.h"
#include <string.h>
#include <limits.h>

/**
 * @brief 写入 16 位小端数据
 */
static void write_u16_le(uint8_t *buf, uint16_t value)
{
    buf[0] = value & 0xFF;
    buf[1] = (value >> 8) & 0xFF;
}

/**
 * @brief 写入 32 位小端数据
 */
static void write_u32_le(uint8_t *buf, uint32_t value)
{
    buf[0] = value & 0xFF;
    buf[1] = (value >> 8) & 0xFF;
    buf[2] = (value >> 16) & 0xFF;
    buf[3] = (value >> 24) & 0xFF;
}

/**
 * @brief 将 PCM 数据封装成 WAV 格式
 */
bool wav_pack_pcm(
    const int16_t *pcm,
    size_t total_samples,
    uint8_t *wav_buffer,
    size_t buffer_size,
    size_t *wav_size)
{
    if (pcm == NULL || wav_buffer == NULL ||
        wav_size == NULL || total_samples == 0)
    {
        return false;
    }

    *wav_size = 0;

    // 计算 PCM 数据长度
    if (total_samples > UINT32_MAX / sizeof(int16_t))
    {
        return false;
    }

    uint32_t data_size = total_samples * sizeof(int16_t);

    // WAV 文件总长度 = 44 字节文件头 + PCM 数据
    size_t total_size = WAV_HEADER_SIZE + (size_t)data_size;

    if (buffer_size < total_size)
    {
        return false;
    }

    uint8_t *header = wav_buffer;

    // RIFF Header
    memcpy(header, "RIFF", 4);
    write_u32_le(header + 4, 36 + data_size);
    memcpy(header + 8, "WAVE", 4);

    // fmt Subchunk
    memcpy(header + 12, "fmt ", 4);
    write_u32_le(header + 16, 16);                   // fmt 长度
    write_u16_le(header + 20, 1);                    // PCM 编码
    write_u16_le(header + 22, WAV_CHANNELS);         // 单声道
    write_u32_le(header + 24, WAV_SAMPLE_RATE);      // 采样率
    write_u32_le(header + 28,
                 WAV_SAMPLE_RATE * WAV_CHANNELS *
                 WAV_BITS_PER_SAMPLE / 8);           // 字节率
    write_u16_le(header + 32,
                 WAV_CHANNELS * WAV_BITS_PER_SAMPLE / 8); // 块对齐
    write_u16_le(header + 34, WAV_BITS_PER_SAMPLE);  // 位深

    // Data Subchunk
    memcpy(header + 36, "data", 4);
    write_u32_le(header + 40, data_size);

    // 复制原始 PCM 数据
    memcpy(wav_buffer + WAV_HEADER_SIZE, pcm, data_size);

    *wav_size = total_size;

    return true;
}
