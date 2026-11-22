#ifndef _SYSTEM_H_
#define _SYSTEM_H_

#define BLE_DATA_MAX_LEN 256

void system_init(void);
void key_task(void *arg);
void waked_task(void *arg);
void audio_record_task(void *arg);
void http_task(void *arg);

#endif
