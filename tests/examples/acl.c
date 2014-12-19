#include "spi.h"
#include "flexpret_threads.h"

#define PWM_NUM_CYCLES 65536
#define PWM_PIN 0x0F 
#define CS_PIN 0x80

// shared variables to communicate data to the PWM module
volatile uint32_t pwm_period = 2;
volatile uint32_t pwm_high_time = 0;

// MMA7455 Register Map
uint8_t MMA_XOUTL = 0x00;  // X-axis output LSB
uint8_t MMA_XOUTH = 0x01;  // X-axis output MSB
uint8_t MMA_YOUTL = 0x02;  // Y-axis output LSB
uint8_t MMA_YOUTH = 0x03;  // Y-axis output MSB
uint8_t MMA_ZOUTL = 0x04;  // Z-axis output LSB
uint8_t MMA_ZOUTH = 0x05;  // Z-axis output MSB
uint8_t MMA_XOUT8 = 0x06;  // X-axis output 8 bits
uint8_t MMA_YOUT8 = 0x07;  // Y-axis output 8 bits
uint8_t MMA_ZOUT8 = 0x08;  // Z-axis output 8 bits

uint8_t MMA_I2CAD = 0x0D;   // I2C device address
uint8_t MMA_STATUS = 0x09;  // status registers
uint8_t MMA_MCTL = 0x16;    // mode control

// Accelerometer driver function prototypes
uint8_t aclReadReg(uint8_t reg);
uint8_t aclWriteReg(uint8_t reg, uint8_t value);

void set_PWM() {gpo_set(PWM_PIN);}
void clr_PWM() {gpo_clear(PWM_PIN);}
void set_CS_acl() {gpo_set(CS_PIN);}
void clr_CS_acl() {gpo_clear(CS_PIN);}

void setup() {
  
  // Set CS pin high since it's active-low
  set_CS_acl();
  sleep_us(250);
  
  // Test accelerometer response by writing to MMA_I2CAD 
  // register and reading it back make sure the value is the same
  aclWriteReg(MMA_I2CAD, 0x9D);
  sleep_us(250);

  uint8_t aclResp = aclReadReg(MMA_I2CAD);
  sleep_us(250);
  if (aclResp != 0x9D) {gpo_write(0xFF);}
  else
  {
     // Bring the accelerometer out of idle
     aclWriteReg(MMA_MCTL, 0b10000101);

     // Start reading accelerometer output continuously
     while (1) loop();
  }
}

void loop() {
  // Read the 8-bit X output register
  uint8_t xVal = aclReadReg(MMA_ZOUT8);
  
  // Scale and normalize accelerometer output to 1g = 16
  if (xVal >= 0x80) {xVal = ~xVal + 1;}
  xVal >>= 2;
  if (xVal > 15) {xVal = 15;}
  
  // Write accelerometer data to GPO
  gpo_write((xVal & 0x0F) | 0x80 );

  pwm_period = 65536;
  pwm_high_time = (1 << xVal);

  sleep_ms(100);
}



// PWM generator function - sets duty cycle of pwm based on 
// shared variable with accelerometer reading
void PWM_generator() {
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
			set_PWM();
			cycle_completion_time = next_period;
		} else if (next_high_time == 0) {
			// always 0
			periodic_delay(&clk, cycle_completion_time << 6);
			clr_PWM();
			cycle_completion_time = next_period;
		} else {
			periodic_delay(&clk, cycle_completion_time << 6);
			set_PWM();
			periodic_delay(&clk, next_high_time << 6);
			clr_PWM();
			cycle_completion_time = next_period - next_high_time;
		}
	}
}

/*
// PWM generator function - sets duty cycle of pwm based on 
// shared variable with accelerometer reading
void PWM_generator() {
        uint32_t clk = get_time();
	while (1)
	{
		// Copies duty_cycle into local variable 
                // since it's volatile
		uint32_t dCycle = duty_cycle;
		if (dCycle > PWM_NUM_CYCLES) dCycle = PWM_NUM_CYCLES;
		uint32_t low_time = PWM_NUM_CYCLES - dCycle;
		set_PWM();
		periodic_delay(&clk, dCycle<<8);
		clr_PWM();
		periodic_delay(&clk, low_time<<8);
	}
}
*/

// Reads one of the accelerometer's registers through SPI
// Returns: value of the register read
uint8_t aclReadReg(uint8_t reg) {
  uint8_t data;

  // Set CS pin low (active)
  clr_CS_acl();
  sleep_us(10);
  
  reg = 0b01111110 & (reg << 1);

  // send read operation and register address
  SPI_transfer(reg, 0, 0);
  // read out data
  data = SPI_transfer(0x00, 0, 0);
  
  // Set CS pin back to high
  set_CS_acl();
  sleep_us(10); 

  return data;
}

// Writes one of the accelerometer's registers through SPI
uint8_t aclWriteReg(uint8_t reg, uint8_t value) {
  uint8_t data;
  
  // Set CS pin low (active)
  clr_CS_acl();
  sleep_us(10); 

  reg = 0b10000000 | (reg << 1);
  
  // send write operation and register address
  SPI_transfer(reg, 0, 0);
  
  // send value to write uint8_to register
  SPI_transfer(value, 0, 0);
  
  // Set CS pin back to high
  set_CS_acl();
  sleep_us(10);  
  
  return data;
}

uint8_t main(void)
{
    //start up 2 threads - one for getting acclerometer data and one for pwm
    hwthread_start(1, setup, NULL);
    hwthread_start(2, PWM_generator, NULL);
    set_slots(SLOT_T1, SLOT_T2, SLOT_D, SLOT_D, SLOT_D, SLOT_D, SLOT_D,SLOT_D);
    set_tmodes_4(TMODE_HZ, TMODE_HA, TMODE_HA, TMODE_HZ);
    while((hwthread_done(1) & hwthread_done(2)) == 0);
    return 1;
}
