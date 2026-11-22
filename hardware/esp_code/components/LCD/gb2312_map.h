#ifndef GB2312_MAP_H
#define GB2312_MAP_H

#include <stdint.h>

typedef struct {
    uint16_t unicode;
    uint16_t gb2312;
} gb2312_map_t;

extern const gb2312_map_t gb2312_map[];
extern const uint16_t gb2312_map_size;

#endif
