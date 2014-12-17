#include "encoding.h"

#ifndef THREADS
#define THREADS 4
#endif

// Added for FlexPRET
#ifndef NULL
typedef unsigned int uint32_t;
#define NULL 0L
#endif

typedef struct hwthread_state {
    void (*func)();
    uint32_t stack_address;
} hwthread_state;

extern volatile hwthread_state startup_state[THREADS];

void hwthread_start(uint32_t tid, void (*func)(), uint32_t stack_address) {
    startup_state[tid].func = func;
    if(stack_address != NULL) {
        startup_state[tid].stack_address = stack_address;
    }
}

uint32_t hwthread_done(uint32_t tid) {
    return (startup_state[tid].func == NULL);
}

#define set_slots(s7, s6, s5, s4, s3, s2, s1, s0) (\
        {write_csr(uarch0, (\
        ((s7 & 0xF) << 28) | \
        ((s6 & 0xF) << 24) | \
        ((s5 & 0xF) << 20) | \
        ((s4 & 0xF) << 16) | \
        ((s3 & 0xF) << 12) | \
        ((s2 & 0xF) <<  8) | \
        ((s1 & 0xF) <<  4) | \
        ((s0 & 0xF) <<  0)   \
        ));})
#define SLOT_T0 0
#define SLOT_T1 1
#define SLOT_T2 2
#define SLOT_T3 3
#define SLOT_T4 4
#define SLOT_T5 5
#define SLOT_T6 6
#define SLOT_T7 7
#define SLOT_S 14
#define SLOT_D 15

#define set_tmodes_4(t3, t2, t1, t0) (\
        {write_csr(uarch1, (\
        ((t3 & 0x3) << 6) | \
        ((t2 & 0x3) << 4) | \
        ((t1 & 0x3) << 2) | \
        ((t0 & 0x3) << 0)   \
        ));})
#define TMODE_HA 0
#define TMODE_HZ 1
#define TMODE_SA 2
#define TMODE_SZ 3
