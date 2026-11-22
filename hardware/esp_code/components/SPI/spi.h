#ifndef __MYSPI_H_
#define __MYSPI_H_

#include "driver/spi_master.h"

#define SCL_PIN  GPIO_NUM_21
#define MOSI_PIN GPIO_NUM_47
#define MISO_PIN GPIO_NUM_13

extern spi_device_handle_t spi2_handle;

void spi2_init(void);
uint8_t spi2_transfer_byte(uint8_t data);
void spi2_write_data(uint8_t *data, int len);
#endif
