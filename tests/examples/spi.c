#include "flexpret_timing.h"
#include "flexpret_io.h"
#include <stdint.h>
#include <stdio.h>

#define PERIOD 10000 

uint8_t make_gpo(uint8_t clock, uint8_t mosi) {
  return ((clock << 2) | mosi);
}
uint8_t data_bit(uint8_t data, uint8_t bit) {
  if (data & (1 << bit)) {
    return 1;
  } else {
    return 0;
  }
}

uint8_t SPI_transfer(uint8_t write_byte, uint8_t cpol, uint8_t cpha)
{
  uint32_t clk;

  uint8_t next_out_byte;
  uint8_t bit = 0;

  uint8_t result = 0;

  // calculate the next GPIO byte before doing the delay to reduce jitter
  if (cpha == 0) {
    next_out_byte = make_gpo(cpol, data_bit(write_byte, 7-bit));
  } else {
    next_out_byte = make_gpo(!cpol, data_bit(write_byte, 7-bit));
  }

  while (bit < 8) {
    // new data on lines here
    if (bit == 0) {
      gpo_write(next_out_byte);
    } else {
      // though gpo_write could be factored out after the loop, it's kept
      // together with periodic_delay to encourage repeatable timing with the
      // second half-cycle
      periodic_delay(&clk, PERIOD/2);
      gpo_write(next_out_byte);
    }

    if (cpha == 0) {
      next_out_byte = make_gpo(!cpol, data_bit(write_byte, 7-bit));
    } else {
      next_out_byte = make_gpo(cpol, data_bit(write_byte, 7-bit));
    }

    if (bit == 0) {
      clk = get_time();
    }
    periodic_delay(&clk, PERIOD/2);
    gpo_write(next_out_byte);  // data sampled here

    result = result << 1;
    result |= data_bit(gpi_read(), 0);

    bit++;
    if (cpha == 0) {
      next_out_byte = make_gpo(cpol, data_bit(write_byte, 7-bit));
    } else {
      next_out_byte = make_gpo(!cpol, data_bit(write_byte, 7-bit));
    }
  }

  next_out_byte = make_gpo(cpol, 0);  // return to idle
  periodic_delay(&clk, PERIOD/2);
  gpo_write(next_out_byte);

  return result;
}

int main(void)
{
    uint8_t result = SPI_transfer(0x4a, 0, 0);
    debug_string(itoa_hex(result));
    debug_string("\n");
    return 1;
}


