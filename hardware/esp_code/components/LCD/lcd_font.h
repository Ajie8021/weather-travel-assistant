#ifndef LCD_FONT_H
#define LCD_FONT_H

#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

/* 支持 16/24/32 三档字体；中文为方形，ASCII 为半宽。 */
bool lcd_set_font_size(uint8_t size);
uint8_t lcd_get_font_size(void);

/* UTF-8 文本绘制，坐标为左上角像素坐标。 */
void lcd_show_text(uint16_t x, uint16_t y, const char *str,
                   uint16_t font_color, uint16_t bg_color);
void text_filter(const char *src, char *dst, size_t dst_size);

#endif
