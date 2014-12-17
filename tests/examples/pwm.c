#include <stdint.h>
#include <stdio.h>
#include "flexpret_timing.h"
#include "flexpret_threads.h"
#include "flexpret_io.h"

#define PWM_PIN 0x0F 
void set_pwm() {gpo_set(PWM_PIN);}
void clr_pwm() {gpo_clear(PWM_PIN);}

// shared variables to communicate data to the PWM module
volatile uint32_t pwm_period = 2;
volatile uint32_t pwm_high_time = 0;

// PWM generator function - sets duty cycle of pwm based on 
// shared variable with accelerometer reading
void pwm_generator() {
        uint32_t clk = get_time();
	uint32_t cycle_completion_time = 0;

	while (1) {
		uint32_t next_period = pwm_period;
		uint32_t next_high_time = pwm_high_time;
		if (next_high_time > next_period) {
			next_high_time = next_period;
		}

		if (next_high_time >= next_period) {
			// always 1
			periodic_delay(&clk, cycle_completion_time << 6);
			set_pwm();
			cycle_completion_time = next_period;
		} else if (next_high_time == 0) {
			// always 0
			periodic_delay(&clk, cycle_completion_time << 6);
			clr_pwm();
			cycle_completion_time = next_period;
		} else {
			periodic_delay(&clk, cycle_completion_time << 6);
			set_pwm();
			periodic_delay(&clk, next_high_time << 6);
			clr_pwm();
			cycle_completion_time = next_period - next_high_time;
		}
	}
}

#define COMMAND_IN_DATA *((volatile uint32_t*)(0xffff8800))
#define COMMAND_IN_VALID *((volatile uint32_t*)(0xffff8801))

#define RESPONSE_OUT_DATA *((volatile uint32_t*)(0xffff8810))
#define RESPONSE_OUT_READY *((volatile uint32_t*)(0xffff8811))

#define OP_PERIOD 0x00
#define OP_HIGH 0x01

void pwm_host() {
    while (1) {
      while (!COMMAND_IN_VALID);

      uint32_t received_command = COMMAND_IN_DATA;
      uint8_t opcode = received_command >> 24;
      uint32_t data = received_command & 0x00ffffff;  // truncate to 24 bits

      if (opcode == OP_PERIOD) {
        pwm_period = data;
      } else if (opcode == OP_HIGH) {
        pwm_high_time = data;
      }
    }
}

uint8_t main(void)
{
    // start another thread for the host interface
    hwthread_start(1, pwm_host, NULL);
    set_slots(SLOT_T0, SLOT_T1, SLOT_D, SLOT_D, SLOT_D, SLOT_D, SLOT_D,SLOT_D);
    set_tmodes_4(TMODE_HZ, TMODE_HZ, TMODE_HA, TMODE_HA);

    pwm_generator();
}

