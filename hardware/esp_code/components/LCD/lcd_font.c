#include "lcd_font.h"

#include <stddef.h>
#include "lcd.h"
#include "gb2312_map.h"

/* lcd.c 中已有该 ASCII 字库定义，这里只引用，不重复定义。 */
extern const unsigned char ascii_3216[][64];

/* ESP-IDF EMBED_FILES "HZK16.bin" 生成的二进制资源符号。 */
extern const uint8_t _binary_HZK16_bin_start[];
extern const uint8_t _binary_HZK16_bin_end[];

#define LCD_FONT_SCREEN_W 240
#define LCD_FONT_SCREEN_H 240
#define GB2312_ROWS       94
#define GB2312_COLS       94
#define HZK16_GLYPH_SIZE  32

static uint8_t s_font_size = 16;

bool lcd_set_font_size(uint8_t size)
{
    if (size != 16 && size != 24 && size != 32) {
        return false;
    }

    s_font_size = size;
    return true;
}

uint8_t lcd_get_font_size(void)
{
    return s_font_size;
}

static bool unicode_to_gb2312(uint16_t unicode, uint16_t *gb_code)
{
    int left = 0;
    int right = (int)gb2312_map_size - 1;

    while (left <= right) {
        int mid = left + (right - left) / 2;
        uint16_t value = gb2312_map[mid].unicode;

        if (value == unicode) {
            *gb_code = gb2312_map[mid].gb2312;
            return true;
        }

        if (value < unicode) {
            left = mid + 1;
        } else {
            right = mid - 1;
        }
    }

    return false;
}

/* 读取一个 UTF-8 码点；非法序列返回 U+FFFD，并至少前进一个字节。 */
static uint32_t utf8_next(const uint8_t **cursor)
{
    const uint8_t *s = *cursor;
    uint32_t cp;

    if (s[0] < 0x80) {
        *cursor = s + 1;
        return s[0];
    }

    if ((s[0] & 0xE0) == 0xC0 &&
        s[1] != '\0' && (s[1] & 0xC0) == 0x80) {
        cp = ((uint32_t)(s[0] & 0x1F) << 6) |
             (uint32_t)(s[1] & 0x3F);
        if (cp >= 0x80) {
            *cursor = s + 2;
            return cp;
        }
    } else if ((s[0] & 0xF0) == 0xE0 &&
               s[1] != '\0' && s[2] != '\0' &&
               (s[1] & 0xC0) == 0x80 &&
               (s[2] & 0xC0) == 0x80) {
        cp = ((uint32_t)(s[0] & 0x0F) << 12) |
             ((uint32_t)(s[1] & 0x3F) << 6) |
             (uint32_t)(s[2] & 0x3F);
        if (cp >= 0x800 && !(cp >= 0xD800 && cp <= 0xDFFF)) {
            *cursor = s + 3;
            return cp;
        }
    } else if ((s[0] & 0xF8) == 0xF0 &&
               s[1] != '\0' && s[2] != '\0' && s[3] != '\0' &&
               (s[1] & 0xC0) == 0x80 &&
               (s[2] & 0xC0) == 0x80 &&
               (s[3] & 0xC0) == 0x80) {
        /* 当前字库是 GB2312/BMP，四字节字符会走缺字回退。 */
        *cursor = s + 4;
        return 0xFFFD;
    }

    *cursor = s + 1;
    return 0xFFFD;
}

static bool hzk16_get_glyph(uint16_t gb_code, const uint8_t **glyph)
{
    uint8_t high = (uint8_t)(gb_code >> 8);
    uint8_t low = (uint8_t)gb_code;
    uint32_t row;
    uint32_t col;
    uint32_t offset;
    uint32_t hzk_size = (uint32_t)(_binary_HZK16_bin_end -
                                  _binary_HZK16_bin_start);

    if (high < 0xA1 || high > 0xFE ||
        low < 0xA1 || low > 0xFE) {
        return false;
    }

    row = high - 0xA1;
    col = low - 0xA1;
    offset = (row * GB2312_COLS + col) * HZK16_GLYPH_SIZE;

    if (offset + HZK16_GLYPH_SIZE > hzk_size) {
        return false;
    }

    *glyph = _binary_HZK16_bin_start + offset;
    return true;
}

static bool hzk16_pixel(const uint8_t *glyph, uint8_t x, uint8_t y)
{
    uint8_t byte = glyph[y * 2 + x / 8];
    return (byte & (uint8_t)(0x80U >> (x % 8))) != 0;
}

/* ASCII 字模原始尺寸 16x32，按当前字号缩放为半宽字符。 */
static bool ascii_pixel(uint8_t chr, uint8_t x, uint8_t y,
                        uint8_t dst_w, uint8_t dst_h)
{
    uint8_t src_x = (uint16_t)x * 16 / dst_w;
    uint8_t src_y = (uint16_t)y * 32 / dst_h;
    uint8_t index = (uint8_t)(chr - ' ');
    uint8_t byte = ascii_3216[index][src_y * 2 + src_x / 8];

    /* 与原 lcd_show_char() 一致：ASCII 字模按低位到高位取点。 */
    return (byte & (uint8_t)(1U << (src_x % 8))) != 0;
}

static void draw_cell(uint16_t x, uint16_t y, uint8_t width,
                      uint8_t height, uint32_t codepoint,
                      uint16_t font_color, uint16_t bg_color)
{
    const uint8_t *glyph = NULL;
    bool is_ascii = (codepoint >= 0x20 && codepoint <= 0x7E);
    bool has_glyph = false;
    uint16_t gb_code = 0;
    uint16_t draw_w = width;
    uint16_t draw_h = height;

    if (x >= LCD_FONT_SCREEN_W || y >= LCD_FONT_SCREEN_H ||
        x + draw_w > LCD_FONT_SCREEN_W ||
        y + draw_h > LCD_FONT_SCREEN_H) {
        return;
    }

    if (is_ascii) {
        has_glyph = true;
    } else if (codepoint <= 0xFFFF &&
               unicode_to_gb2312((uint16_t)codepoint, &gb_code)) {
        has_glyph = hzk16_get_glyph(gb_code, &glyph);
    }

    /* 不在 GB2312 字库中的字符用 '?' 代替。 */
    if (!has_glyph) {
        return;
    }

    lcd_set_window(x, y, x + draw_w - 1, y + draw_h - 1);

    for (uint16_t py = 0; py < draw_h; py++) {
        for (uint16_t px = 0; px < draw_w; px++) {
            bool set;

            if (is_ascii) {
                set = ascii_pixel((uint8_t)codepoint, (uint8_t)px,
                                  (uint8_t)py, (uint8_t)draw_w,
                                  (uint8_t)draw_h);
            } else {
                uint8_t sx = (uint16_t)px * 16 / draw_w;
                uint8_t sy = (uint16_t)py * 16 / draw_h;
                set = hzk16_pixel(glyph, sx, sy);
            }

            lcd_write_data16(set ? font_color : bg_color);
        }
    }
}

void lcd_show_text(uint16_t x, uint16_t y, const char *str,
                   uint16_t font_color, uint16_t bg_color)
{
    const uint8_t *cursor = (const uint8_t *)str;
    const uint16_t line_start_x = x;
    const uint8_t font_h = s_font_size;
    const uint8_t ascii_w = s_font_size / 2;

    if (str == NULL) {
        return;
    }

    while (*cursor != '\0') {
        uint32_t cp = utf8_next(&cursor);
        uint8_t cell_w;

        if (cp == '\r') {
            continue;
        }

        if (cp == '\n') {
            x = line_start_x;
            y += font_h;
            if (y + font_h > LCD_FONT_SCREEN_H) {
                break;
            }
            continue;
        }

        cell_w = (cp < 0x80) ? ascii_w : font_h;

        /* 超出右边界时自动换行。 */
        if (x + cell_w > LCD_FONT_SCREEN_W) {
            x = line_start_x;
            y += font_h;
        }

        if (y + font_h > LCD_FONT_SCREEN_H) {
            break;
        }

        draw_cell(x, y, cell_w, font_h, cp, font_color, bg_color);
        x += cell_w;
    }
}

void text_filter(const char *src, char *dst, size_t dst_size)
{
    if (src == NULL || dst == NULL || dst_size == 0)
        return;

    size_t i = 0;
    size_t j = 0;
    uint8_t line_width = 0;

    while (src[i] != '\0' && j < dst_size - 1)
    {
        // 跳过 Markdown 符号
        if (src[i] == '*' || src[i] == '#' ||
            src[i] == '|' || src[i] == '`' ||
            src[i] == '>')
        {
            i++;
            continue;
        }

        // 跳过 Markdown 分隔线
        if (src[i] == '-' && src[i + 1] == '-' &&
            src[i + 2] == '-')
        {
            i += 3;
            continue;
        }

        // 处理换行
        if (src[i] == '\r' || src[i] == '\n')
        {
            if (src[i] == '\r' && src[i + 1] == '\n')
                i += 2;
            else
                i++;

            if (j > 0 && dst[j - 1] != '\n')
            {
                dst[j++] = '\n';
                line_width = 0;
            }
            continue;
        }

        // 合并连续空格
        if (src[i] == ' ')
        {
            if (j > 0 && dst[j - 1] != ' ' &&
                dst[j - 1] != '\n')
            {
                dst[j++] = ' ';
                line_width++;
            }

            i++;
            continue;
        }

        // UTF-8 中文字符按 2 个英文字符宽度计算
        uint8_t char_len = 1;
        uint8_t char_width = 1;

        if ((uint8_t)src[i] >= 0xE0 &&
            (uint8_t)src[i] <= 0xEF)
        {
            char_len = 3;
            char_width = 2;
        }

        // 自动换行，每行最多 30 个英文字符宽度
        if (line_width + char_width > 30)
        {
            if (j >= dst_size - 1)
                break;

            dst[j++] = '\n';
            line_width = 0;
        }

        if (j + char_len >= dst_size)
            break;

        for (uint8_t k = 0; k < char_len; k++)
        {
            dst[j++] = src[i++];
        }

        line_width += char_width;
    }

    dst[j] = '\0';
}
