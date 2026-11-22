#include "http_client.h"

#include "cJSON.h"
#include <strings.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <limits.h>
#include <string.h>
#include <strings.h>
#include "esp_heap_caps.h"

#include "esp_log.h"
#include "esp_http_client.h"
#include "esp_crt_bundle.h"
#include "cJSON.h"
#include "wav.h"

// 分配 PSRAM 空间保存服务器响应
#define VOICE_RESPONSE_MAX_SIZE (512 * 1024)

#define SERVER_URL \
    "https://weather-travel-assisant-o9c4j.ondigitalocean.app/"
#define SESSION_ID "esp32s3_user_001"
#define RESPONSE_SIZE 4096

static const char *TAG = "AI_CLIENT";

// 接收服务器响应
static char response_buffer[RESPONSE_SIZE];
static int response_len = 0;

#define VOICE_SERVER_URL \
    "https://weather-travel-assisant-o9c4j.ondigitalocean.app/api/voice/chat"

#define VOICE_BOUNDARY "----ESP32S3VoiceBoundary2026"
#define VOICE_UPLOAD_CHUNK_SIZE 1024
static const char *VOICE_TAG = "VOICE_CLIENT";

static char voice_content_type[256] = {0};

// HTTP 事件回调
static esp_err_t http_event_handler(esp_http_client_event_t *evt)
{
    switch (evt->event_id)
    {
        case HTTP_EVENT_ON_DATA:

            if (response_len + evt->data_len < RESPONSE_SIZE)
            {
                memcpy(response_buffer + response_len,
                       evt->data,
                       evt->data_len);

                response_len += evt->data_len;

                response_buffer[response_len] = '\0';
            }
            else
            {
                ESP_LOGE(TAG, "Response buffer overflow");
                return ESP_FAIL;
            }

            break;

        default:
            break;
    }

    return ESP_OK;
}

// 查找指定字节序列
static uint8_t *find_bytes(
    uint8_t *data,
    size_t data_len,
    const char *target,
    size_t target_len)
{
    if (data == NULL || target == NULL || target_len == 0 ||
        data_len < target_len)
    {
        return NULL;
    }

    for (size_t i = 0; i <= data_len - target_len; i++)
    {
        if (memcmp(data + i, target, target_len) == 0)
        {
            return data + i;
        }
    }

    return NULL;
}

// 提取服务器 boundary
static bool extract_response_boundary(
    const char *content_type,
    char *boundary,
    size_t boundary_size)
{
    if (content_type == NULL ||
        boundary == NULL ||
        boundary_size == 0)
    {
        return false;
    }

    const char *p = strstr(content_type, "boundary=");

    if (p == NULL)
    {
        return false;
    }

    p += strlen("boundary=");

    // 支持 boundary="xxxx" 格式
    bool quoted = (*p == '"');

    if (quoted)
    {
        p++;
    }

    size_t len = 0;

    while (p[len] != '\0')
    {
        if (quoted)
        {
            if (p[len] == '"')
                break;
        }
        else
        {
            if (p[len] == ';' ||
                p[len] == ' ' ||
                p[len] == '\r' ||
                p[len] == '\n')
            {
                break;
            }
        }

        len++;
    }

    if (len == 0 || len >= boundary_size)
    {
        return false;
    }

    memcpy(boundary, p, len);
    boundary[len] = '\0';

    return true;
}

// 读取 WAV 小端数据
static uint16_t read_le16(const uint8_t *p)
{
    return (uint16_t)p[0] |
           ((uint16_t)p[1] << 8);
}

static uint32_t read_le32(const uint8_t *p)
{
    return (uint32_t)p[0] |
           ((uint32_t)p[1] << 8) |
           ((uint32_t)p[2] << 16) |
           ((uint32_t)p[3] << 24);
}
// 检查 WAV 格式
static bool validate_response_wav(
    const uint8_t *wav,
    size_t wav_size)
{
    if (wav == NULL || wav_size < 44)
    {
        ESP_LOGE(VOICE_TAG, "WAV 数据长度不足");
        return false;
    }

    if (memcmp(wav, "RIFF", 4) != 0 ||
        memcmp(wav + 8, "WAVE", 4) != 0)
    {
        ESP_LOGE(VOICE_TAG, "无效的 RIFF/WAVE 文件");
        return false;
    }

    uint32_t riff_size = read_le32(wav + 4);

    if ((uint64_t)riff_size + 8 != wav_size)
    {
        ESP_LOGE(VOICE_TAG,
                 "WAV 文件长度不匹配: header=%lu, actual=%u",
                 (unsigned long)(riff_size + 8),
                 (unsigned int)wav_size);
        return false;
    }

    if (memcmp(wav + 12, "fmt ", 4) != 0)
    {
        ESP_LOGE(VOICE_TAG, "WAV fmt 块无效");
        return false;
    }

    uint16_t audio_format = read_le16(wav + 20);
    uint16_t channels = read_le16(wav + 22);
    uint32_t sample_rate = read_le32(wav + 24);
    uint32_t byte_rate = read_le32(wav + 28);
    uint16_t block_align = read_le16(wav + 32);
    uint16_t bits_per_sample = read_le16(wav + 34);

    uint32_t data_size = read_le32(wav + 40);

    if (audio_format != 1 ||
        channels != 1 ||
        sample_rate != 16000 ||
        byte_rate != 32000 ||
        block_align != 2 ||
        bits_per_sample != 16)
    {
        ESP_LOGE(VOICE_TAG, "WAV 音频参数不符合要求");
        ESP_LOGE(VOICE_TAG,
                 "format=%u channels=%u rate=%lu bits=%u",
                 audio_format,
                 channels,
                 (unsigned long)sample_rate,
                 bits_per_sample);

        return false;
    }

    if ((uint64_t)data_size + 44 != wav_size)
    {
        ESP_LOGE(VOICE_TAG, "WAV PCM 数据长度不匹配");
        return false;
    }

    ESP_LOGI(VOICE_TAG, "WAV 格式检查通过");
    ESP_LOGI(VOICE_TAG, "采样率: %lu Hz",
             (unsigned long)sample_rate);
    ESP_LOGI(VOICE_TAG, "声道数: %u", channels);
    ESP_LOGI(VOICE_TAG, "位深: %u bit", bits_per_sample);
    ESP_LOGI(VOICE_TAG, "PCM 数据: %lu bytes",
             (unsigned long)data_size);

    // 少于 100ms 的音频通常不可能完整表达正常语音回答
    if (data_size < 3200)
    {
        ESP_LOGE(VOICE_TAG,
                 "音频数据过短，少于 100ms，拒绝作为语音播放");
        return false;
    }

    return true;
}

// 解析 multipart 响应
static esp_err_t parse_voice_response(
    uint8_t *response,
    size_t response_len,
    const char *content_type,
    char *reply,
    size_t reply_size)
{
    if (reply == NULL || reply_size == 0)
    {
        return ESP_ERR_INVALID_ARG;
    }

    reply[0] = '\0';
    char boundary[128] = {0};

    if (!extract_response_boundary(
            content_type,
            boundary,
            sizeof(boundary)))
    {
        ESP_LOGE(VOICE_TAG, "无法提取服务器响应 boundary");
        return ESP_ERR_INVALID_RESPONSE;
    }

    ESP_LOGI(VOICE_TAG, "Response boundary: %s", boundary);

    char delimiter[132];

    snprintf(delimiter, sizeof(delimiter),
             "--%s", boundary);

    size_t delimiter_len = strlen(delimiter);

    // 查找 multipart 第一段
    uint8_t *current = find_bytes(
        response,
        response_len,
        delimiter,
        delimiter_len);

    if (current == NULL)
    {
        ESP_LOGE(VOICE_TAG, "没有找到 multipart 起始边界");
        return ESP_ERR_INVALID_RESPONSE;
    }

    bool metadata_found = false;

    while (current != NULL)
    {
        size_t remaining = response_len - (current - response);

        // 最后一段 boundary：--boundary--
        if (remaining >= delimiter_len + 2 &&
            memcmp(current + delimiter_len, "--", 2) == 0)
        {
            break;
        }

        // boundary 后面必须是 CRLF
        if (remaining < delimiter_len + 2 ||
            memcmp(current + delimiter_len, "\r\n", 2) != 0)
        {
            ESP_LOGE(VOICE_TAG, "multipart boundary 格式错误");
            return ESP_ERR_INVALID_RESPONSE;
        }

        uint8_t *header_start = current + delimiter_len + 2;

        size_t header_remaining =
            response_len - (header_start - response);

        uint8_t *header_end = find_bytes(
            header_start,
            header_remaining,
            "\r\n\r\n",
            4);

        if (header_end == NULL)
        {
            ESP_LOGE(VOICE_TAG, "multipart part 缺少头部结束符");
            return ESP_ERR_INVALID_RESPONSE;
        }

        uint8_t *body_start = header_end + 4;

        size_t body_remaining =
            response_len - (body_start - response);

        // 当前 part 的结束位置
        char next_delimiter[140];

        snprintf(next_delimiter,
                 sizeof(next_delimiter),
                 "\r\n--%s",
                 boundary);

        size_t next_delimiter_len = strlen(next_delimiter);

        uint8_t *next_part = find_bytes(
            body_start,
            body_remaining,
            next_delimiter,
            next_delimiter_len);

        if (next_part == NULL)
        {
            ESP_LOGE(VOICE_TAG, "multipart part 缺少结束边界");
            return ESP_ERR_INVALID_RESPONSE;
        }

        size_t body_len = next_part - body_start;

        // 复制当前 part 的头部，便于判断 name
        size_t header_len = header_end - header_start;

        char part_header[512];

        if (header_len >= sizeof(part_header))
        {
            ESP_LOGE(VOICE_TAG, "multipart part 头部过长");
            return ESP_ERR_INVALID_SIZE;
        }

        memcpy(part_header, header_start, header_len);
        part_header[header_len] = '\0';

        ESP_LOGI(VOICE_TAG,
                 "Part header: %s",
                 part_header);

        // 解析 metadata
        if (strstr(part_header, "name=\"metadata\"") != NULL)
        {
            ESP_LOGI(VOICE_TAG, "发现 metadata part");

            cJSON *json = cJSON_ParseWithLength(
                (const char *)body_start,
                body_len);

            if (json == NULL)
            {
                ESP_LOGE(VOICE_TAG, "metadata JSON 解析失败");
                return ESP_ERR_INVALID_RESPONSE;
            }

            cJSON *question = cJSON_GetObjectItemCaseSensitive(
                json, "question");

            cJSON *answer = cJSON_GetObjectItemCaseSensitive(
                json, "answer");

            cJSON *session_id = cJSON_GetObjectItemCaseSensitive(
                json, "sessionId");

            cJSON *has_audio = cJSON_GetObjectItemCaseSensitive(
                json, "hasAudio");

            cJSON *error_message = cJSON_GetObjectItemCaseSensitive(
                json, "errorMessage");

            if (cJSON_IsString(question))
            {
                ESP_LOGI(VOICE_TAG, "识别文本: %s",
                         question->valuestring);
            }

            if (cJSON_IsString(answer))
            {
                snprintf(reply, reply_size, "%s",
                        answer->valuestring);

                ESP_LOGI(VOICE_TAG, "AI 回复: %s", reply);
            }

            if (cJSON_IsString(session_id))
            {
                ESP_LOGI(VOICE_TAG, "Session ID: %s",
                         session_id->valuestring);
            }

            if (cJSON_IsBool(has_audio))
            {
                ESP_LOGI(VOICE_TAG, "hasAudio: %s",
                         cJSON_IsTrue(has_audio) ? "true" : "false");
            }

            if (cJSON_IsString(error_message))
            {
                ESP_LOGI(VOICE_TAG, "errorMessage: %s",
                         error_message->valuestring);
            }

            metadata_found = true;

            cJSON_Delete(json);
        }

        // 跳到下一段 boundary
        current = next_part + 2;
    }

    if (!metadata_found || reply[0] == '\0')
    {
        ESP_LOGE(VOICE_TAG, "没有获取到有效文本回答");
        return ESP_ERR_INVALID_RESPONSE;
    }

    ESP_LOGI(VOICE_TAG, "文本响应解析完成");

    return ESP_OK;
}

// 发送问题
esp_err_t ai_send_message(const char *message, 
    char *reply, size_t reply_size)
{
    if (message == NULL || reply == NULL || reply_size == 0)
    {
        return ESP_ERR_INVALID_ARG;
    }

    reply[0] = '\0';

    response_len = 0;
    memset(response_buffer, 0, sizeof(response_buffer));

    // 1. 创建 JSON 请求体
    cJSON *root = cJSON_CreateObject();

    if (root == NULL)
    {
        return ESP_ERR_NO_MEM;
    }

    cJSON_AddStringToObject(root, "sessionId", SESSION_ID);
    cJSON_AddStringToObject(root, "message", message);

    char *post_data = cJSON_PrintUnformatted(root);

    cJSON_Delete(root);

    if (post_data == NULL)
    {
        return ESP_ERR_NO_MEM;
    }

    ESP_LOGI(TAG, "Request: %s", post_data);


    // 2. 配置 HTTP Client
    esp_http_client_config_t config = {
        .url = SERVER_URL,
        .method = HTTP_METHOD_POST,
        .event_handler = http_event_handler,
        .timeout_ms = 30000,

        // 使用 ESP-IDF 证书包验证 HTTPS 服务器
        .crt_bundle_attach = esp_crt_bundle_attach
    };

    esp_http_client_handle_t client =
        esp_http_client_init(&config);

    if (client == NULL)
    {
        free(post_data);
        return ESP_FAIL;
    }


    // 3. 设置请求头
    esp_http_client_set_header(
        client,
        "Content-Type",
        "application/json"
    );


    // 4. 设置请求数据
    esp_http_client_set_post_field(
        client,
        post_data,
        strlen(post_data)
    );


    // 5. 发送请求
    esp_err_t err = esp_http_client_perform(client);

    if (err == ESP_OK)
    {
        int status_code =
            esp_http_client_get_status_code(client);

        ESP_LOGI(TAG, "HTTP Status: %d", status_code);

        if (status_code == 200)
        {
            // 6. 解析 JSON 响应
            cJSON *response =
                cJSON_Parse(response_buffer);

            if (response == NULL)
            {
                ESP_LOGE(TAG, "JSON parse failed");
                err = ESP_FAIL;
            }
            else
            {
                cJSON *reply_json =
                    cJSON_GetObjectItem(response, "reply");

                if (cJSON_IsString(reply_json) &&
                    reply_json->valuestring != NULL)
                {
                    snprintf(reply, reply_size, "%s",
                            reply_json->valuestring);

                    ESP_LOGI(TAG, "AI Reply: %s", reply);
                }
                else
                {
                    ESP_LOGE(TAG, "Reply field not found");
                    err = ESP_FAIL;
                }

                cJSON_Delete(response);
            }
        }
        else
        {
            ESP_LOGE(TAG, "Server returned HTTP %d",
                     status_code);

            ESP_LOGE(TAG, "Response: %s", response_buffer);

            err = ESP_FAIL;
        }
    }
    else
    {
        ESP_LOGE(TAG, "HTTP request failed: %s",
                 esp_err_to_name(err));
    }


    // 7. 释放资源
    esp_http_client_cleanup(client);

    free(post_data);

    return err;
}

// 语音接口响应事件：只关注响应头，不缓存二进制音频
static esp_err_t voice_http_event_handler(esp_http_client_event_t *evt)
{
    switch (evt->event_id)
    {
        case HTTP_EVENT_ON_HEADER:
            if (evt->header_key != NULL &&
                evt->header_value != NULL)
            {
                ESP_LOGI("VOICE_CLIENT",
                         "Response Header: %s: %s",
                         evt->header_key,
                         evt->header_value);

                if (strcasecmp(evt->header_key, "Content-Type") == 0)
                {
                    snprintf(voice_content_type,
                             sizeof(voice_content_type),
                             "%s",
                             evt->header_value);
                }
            }
            break;

        default:
            break;
    }

    return ESP_OK;
}

// 保证请求数据完整写入
static esp_err_t voice_write_all(
    esp_http_client_handle_t client,
    const uint8_t *data,
    size_t length)
{
    if (client == NULL || data == NULL)
    {
        return ESP_ERR_INVALID_ARG;
    }

    size_t written = 0;

    while (written < length)
    {
        size_t remaining = length - written;

        int chunk = (remaining > VOICE_UPLOAD_CHUNK_SIZE)
                    ? VOICE_UPLOAD_CHUNK_SIZE
                    : (int)remaining;

        int ret = esp_http_client_write(
            client,
            (const char *)(data + written),
            chunk
        );

        if (ret < 0)
        {
            ESP_LOGE(VOICE_TAG,
                     "HTTP write error: ret=%d, written=%u/%u",
                     ret,
                     (unsigned int)written,
                     (unsigned int)length);

            return ESP_FAIL;
        }

        if (ret == 0)
        {
            ESP_LOGE(VOICE_TAG,
                     "HTTP write returned 0: written=%u/%u",
                     (unsigned int)written,
                     (unsigned int)length);

            return ESP_FAIL;
        }

        written += (size_t)ret;
    }

    ESP_LOGI(VOICE_TAG,
             "HTTP write completed: %u bytes",
             (unsigned int)written);

    return ESP_OK;
}

// 发送 WAV 音频到后端
esp_err_t ai_send_voice(
    const uint8_t *wav_data,
    size_t wav_size,
    char *reply,
    size_t reply_size,
    voice_status_callback_t status_callback)
{
    if (reply == NULL || reply_size == 0)
    {
        return ESP_ERR_INVALID_ARG;
    }
    reply[0] = '\0';
    if (wav_data == NULL || wav_size <= WAV_HEADER_SIZE)
    {
        ESP_LOGE(VOICE_TAG, "Invalid WAV data");
        return ESP_ERR_INVALID_ARG;
    }

    if (wav_size > INT_MAX)
    {
        ESP_LOGE(VOICE_TAG, "WAV data too large");
        return ESP_ERR_INVALID_SIZE;
    }

    ESP_LOGI(VOICE_TAG, "开始上传 WAV");
    ESP_LOGI(VOICE_TAG, "WAV size: %u bytes",
             (unsigned int)wav_size);

    // 1. 构造 multipart 数据段
    char session_part[256];
    char file_part[256];
    char end_part[128];

    snprintf(session_part, sizeof(session_part),
             "--%s\r\n"
             "Content-Disposition: form-data; name=\"sessionId\"\r\n"
             "\r\n"
             "%s\r\n",
             VOICE_BOUNDARY,
             SESSION_ID);

    snprintf(file_part, sizeof(file_part),
             "--%s\r\n"
             "Content-Disposition: form-data; name=\"file\"; filename=\"record.wav\"\r\n"
             "Content-Type: audio/wav\r\n"
             "\r\n",
             VOICE_BOUNDARY);

    snprintf(end_part, sizeof(end_part),
             "\r\n--%s--\r\n",
             VOICE_BOUNDARY);

    size_t session_len = strlen(session_part);
    size_t file_len = strlen(file_part);
    size_t end_len = strlen(end_part);

    size_t content_length =
        session_len + file_len + wav_size + end_len;

    if (content_length > INT_MAX)
    {
        return ESP_ERR_INVALID_SIZE;
    }

    // 2. 配置 HTTP Client
    esp_http_client_config_t config = {
        .url = VOICE_SERVER_URL,
        .method = HTTP_METHOD_POST,
        .event_handler = voice_http_event_handler,
        .timeout_ms = 90000,
        .crt_bundle_attach = esp_crt_bundle_attach
    };

    esp_http_client_handle_t client =
        esp_http_client_init(&config);

    if (client == NULL)
    {
        ESP_LOGE(VOICE_TAG, "HTTP client init failed");
        return ESP_FAIL;
    }

    esp_err_t err = ESP_OK;

    // 3. 设置请求头
    char content_type[128];

    snprintf(content_type, sizeof(content_type),
             "multipart/form-data; boundary=%s",
             VOICE_BOUNDARY);

    err = esp_http_client_set_header(
        client,
        "Content-Type",
        content_type
    );

    if (err != ESP_OK)
        goto cleanup;

    err = esp_http_client_set_header(
        client,
        "Accept",
        "multipart/mixed"
    );

    if (err != ESP_OK)
        goto cleanup;

    // 4. 打开 HTTP 连接，声明请求体长度
    err = esp_http_client_open(client, (int)content_length);

    if (err != ESP_OK)
    {
        ESP_LOGE(VOICE_TAG, "HTTP open failed: %s",
                 esp_err_to_name(err));
        goto cleanup;
    }
    if (status_callback)
    {
        status_callback("正在上传语音...");
    }

    // 5. 依次上传 sessionId、文件头、WAV 数据、结束边界
    err = voice_write_all(
        client,
        (const uint8_t *)session_part,
        session_len
    );

    if (err != ESP_OK)
        goto close_connection;

    err = voice_write_all(
        client,
        (const uint8_t *)file_part,
        file_len
    );

    if (err != ESP_OK)
        goto close_connection;

    // WAV 数据分块上传
    err = voice_write_all(
        client,
        wav_data,
        wav_size
    );

    if (err != ESP_OK)
        goto close_connection;

    err = voice_write_all(
        client,
        (const uint8_t *)end_part,
        end_len
    );

    if (err != ESP_OK)
        goto close_connection;

    if (status_callback)
    {
        status_callback("等待服务器响应...");
    }

    // 6. 接收 HTTP 响应头
    int64_t response_length =
        esp_http_client_fetch_headers(client);

    int status_code =
        esp_http_client_get_status_code(client);

    ESP_LOGI(VOICE_TAG, "HTTP Status: %d", status_code);
    ESP_LOGI(VOICE_TAG, "Response Length: %lld",
             (long long)response_length);

    if (status_code < 200 || status_code >= 300)
    {
        ESP_LOGE(VOICE_TAG, "Voice request failed, HTTP %d",
                 status_code);

        err = ESP_FAIL;
        goto close_connection;
    }

    if (response_length > VOICE_RESPONSE_MAX_SIZE)
    {
        ESP_LOGE(VOICE_TAG, "服务器响应超过最大容量");
        err = ESP_ERR_INVALID_SIZE;
        goto close_connection;
    }

    // 7. 分配响应缓冲区
    // 多分配一个字节，方便后续字符串处理
    uint8_t *response_buffer = heap_caps_malloc(
        VOICE_RESPONSE_MAX_SIZE + 1,
        MALLOC_CAP_SPIRAM | MALLOC_CAP_8BIT
    );

    if (response_buffer == NULL)
    {
        ESP_LOGE(VOICE_TAG, "响应缓冲区分配失败");
        err = ESP_ERR_NO_MEM;
        goto close_connection;
    }

    size_t response_len = 0;
    bool response_overflow = false;

    // 8. 循环接收响应体
    while (1)
    {
        size_t remaining =
            VOICE_RESPONSE_MAX_SIZE + 1 - response_len;

        int read_len = esp_http_client_read(
            client,
            (char *)response_buffer + response_len,
            (int)remaining
        );

        if (read_len < 0)
        {
            ESP_LOGE(VOICE_TAG, "读取响应失败");
            err = ESP_FAIL;
            break;
        }

        if (read_len == 0)
        {
            break;
        }

        response_len += read_len;

        if (response_len > VOICE_RESPONSE_MAX_SIZE)
        {
            ESP_LOGE(VOICE_TAG, "响应超过缓冲区最大容量");
            response_overflow = true;
            err = ESP_ERR_INVALID_SIZE;
            break;
        }
    }

    ESP_LOGI(VOICE_TAG, "响应总大小: %u bytes",
             (unsigned int)response_len);

    if (response_overflow)
    {
        ESP_LOGE(VOICE_TAG, "响应过大，停止解析");
    }
    else if (err == ESP_OK)
    {
        // 9. 解析 multipart 响应
        response_buffer[response_len] = '\0';

        ESP_LOGI(VOICE_TAG, "Content-Type: %s",
                 voice_content_type);

        err = parse_voice_response(
            response_buffer,
            response_len,
            voice_content_type,
            reply,
            reply_size
        );
    }

    heap_caps_free(response_buffer);

    if (err == ESP_OK)
    {
        ESP_LOGI(VOICE_TAG, "Voice response processed successfully");
    }
    else
    {
        ESP_LOGE(VOICE_TAG, "Voice response processing failed: %s",
                 esp_err_to_name(err));
    }

close_connection:
    esp_http_client_close(client);

cleanup:
    esp_http_client_cleanup(client);

    return err;
}
