#include "flexpret_timing.h"
#include "flexpret_io.h"
#include <stdint.h>
#include <stdio.h>

int main(void)
{
    unsigned int i;
    for (i=0; i<100; i++) {
        gpo_write(0xaa);
    }
    
    *((unsigned int*)0xffff8800) = 0;

    return 1;
}


