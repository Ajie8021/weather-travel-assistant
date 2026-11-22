#include "wifi.h"

#include "esp_wifi.h"
#include "esp_event.h"
#include "esp_netif.h"
#include "esp_log.h"

#define WIFI_CONNECTED_BIT BIT0
#define WIFI_FAIL_BIT       BIT1

#include "lcd.h"

#define MAX_RETRY  5

static const char *TAG = "WIFI";
static int retry_count = 0;

static EventGroupHandle_t wifi_event_group = NULL;

// Wi-Fi 事件函数
void wifista_event_handler(void *event_handler_arg,
                           esp_event_base_t event_base,
                           int32_t event_id,
                           void *event_data)
{
    if (event_base == WIFI_EVENT)
    {
        switch (event_id)
        {
            case WIFI_EVENT_STA_START:
                // Wi-Fi 启动成功，开始连接
                ESP_LOGI(TAG, "WiFi starting...");
                esp_wifi_connect();
                break;

            case WIFI_EVENT_STA_CONNECTED:
                ESP_LOGI(TAG, "WiFi connected to AP");
                break;
            
            case WIFI_EVENT_STA_DISCONNECTED:
                // 清除连接成功标志
                xEventGroupClearBits(wifi_event_group, WIFI_CONNECTED_BIT);
                xEventGroupSetBits(wifi_event_group, WIFI_FAIL_BIT);
                ESP_LOGW(TAG, "WiFi disconnected");
                lcd_show_string(1, 1, "disconnected",
                                YELLOW, BLACK);

                if (retry_count < MAX_RETRY)
                {
                    esp_wifi_connect();
                    retry_count++;

                    ESP_LOGI(TAG, "Retry: %d", retry_count);
                }
                else
                {
                    ESP_LOGE(TAG, "WiFi connection failed");
                }
                break;
            default:
                break;
        }
    }

    else if (event_base == IP_EVENT)
    {
        if (event_id == IP_EVENT_STA_GOT_IP)
        {
            // 设置 Wi-Fi 连接成功标志
            xEventGroupSetBits(wifi_event_group, WIFI_CONNECTED_BIT);
            xEventGroupClearBits(wifi_event_group, WIFI_FAIL_BIT);
            esp_netif_ip_info_t *ip_data =
                (esp_netif_ip_info_t *)event_data;

            retry_count = 0;
            
            lcd_clear(BLACK);
            // 获取 IP 成功，显示连接状态
            lcd_show_string(1, 1, "connected   ",
                            YELLOW, BLACK);

            lcd_show_num(2, 1,
                         esp_ip4_addr1_16(&ip_data->ip),
                         3, GREEN, BLACK);

            lcd_show_string(2, 4, ".", GREEN, BLACK);

            lcd_show_num(2, 5,
                         esp_ip4_addr2_16(&ip_data->ip),
                         3, GREEN, BLACK);

            lcd_show_string(2, 8, ".", GREEN, BLACK);

            lcd_show_num(2, 9,
                         esp_ip4_addr3_16(&ip_data->ip),
                         3, GREEN, BLACK);

            lcd_show_string(2, 12, ".", GREEN, BLACK);

            lcd_show_num(2, 13,
                         esp_ip4_addr4_16(&ip_data->ip),
                         3, GREEN, BLACK);

            ESP_LOGI(TAG, "Got IP: " IPSTR,
                     IP2STR(&ip_data->ip));
        }
    }
}

// Wi-Fi 初始化
esp_err_t wifi_init(EventGroupHandle_t g_event_group)
{
    wifi_event_group = g_event_group;

    // 1. 初始化网络接口
    ESP_ERROR_CHECK(esp_netif_init());

    // 2. 创建默认事件循环
    ESP_ERROR_CHECK(esp_event_loop_create_default());

    // 3. 创建 STA 网络接口
    esp_netif_create_default_wifi_sta();

    // 4. 初始化 Wi-Fi
    wifi_init_config_t wifi_init_struct =
        WIFI_INIT_CONFIG_DEFAULT();

    ESP_ERROR_CHECK(esp_wifi_init(&wifi_init_struct));

    // 5. 注册 Wi-Fi 事件
    ESP_ERROR_CHECK(
        esp_event_handler_register(
            WIFI_EVENT,
            ESP_EVENT_ANY_ID,
            &wifista_event_handler,
            NULL
        )
    );

    // 6. 注册 IP 事件
    ESP_ERROR_CHECK(
        esp_event_handler_register(
            IP_EVENT,
            IP_EVENT_STA_GOT_IP,
            &wifista_event_handler,
            NULL
        )
    );

    // 7. 设置 STA 模式
    ESP_ERROR_CHECK(esp_wifi_set_mode(WIFI_MODE_STA));

    // 8. 配置 Wi-Fi 参数
    wifi_config_t wifi_config_struct = {
        .sta = {
            .ssid = DEFAULT_SSID,
            .password = DEFAULT_PWD
        }
    };

    ESP_ERROR_CHECK(
        esp_wifi_set_config(
            WIFI_IF_STA,
            &wifi_config_struct
        )
    );

    // 9. 启动 Wi-Fi
    ESP_ERROR_CHECK(esp_wifi_start());

    ESP_LOGI(TAG, "WiFi initialization complete");
    return ESP_OK;
}

// 等待 Wi-Fi 连接成功
esp_err_t wifi_wait_connected(uint32_t timeout_ms)
{
    EventBits_t bits = xEventGroupWaitBits(
        wifi_event_group,
        WIFI_CONNECTED_BIT,  // 等待的事件位
        pdFALSE,              // 不自动清除事件位(任务解除阻塞时，是否自动清除等待的事件位)
        pdTRUE,               // 等待所有指定事件位
        pdMS_TO_TICKS(timeout_ms)
    );

    if (bits & WIFI_CONNECTED_BIT)
    {
        ESP_LOGI(TAG, "WiFi is ready");
        return ESP_OK;
    }

    ESP_LOGE(TAG, "WiFi connection timeout");
    return ESP_ERR_TIMEOUT;
}
