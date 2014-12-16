#include "flexpret_timing.h"
#include "flexpret_io.h"
#include <stdint.h>
#include <stdio.h>

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

uint8_t SPI_transfer(uint8_t write_byte, uint32_t period, uint8_t cpol, uint8_t cpha)
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
      clk = get_time();
      periodic_delay(&clk, period/2);
      gpo_write(next_out_byte);
    } else {
      // though gpo_write could be factored out after the loop, it's kept
      // together with periodic_delay to encourage repeatable timing with the
      // second half-cycle
      periodic_delay(&clk, period/2);
      gpo_write(next_out_byte);
    }

    if (cpha == 0) {
      next_out_byte = make_gpo(!cpol, data_bit(write_byte, 7-bit));
    } else {
      next_out_byte = make_gpo(cpol, data_bit(write_byte, 7-bit));
    }

    periodic_delay(&clk, period/2);
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
  periodic_delay(&clk, period/2);
  gpo_write(next_out_byte);

  return result;
}

#define COMMAND_IN_DATA *((volatile uint32_t*)(0xffff8800))
#define COMMAND_IN_VALID *((volatile uint32_t*)(0xffff8801))

#define RESPONSE_OUT_DATA *((volatile uint32_t*)(0xffff8810))
#define RESPONSE_OUT_READY *((volatile uint32_t*)(0xffff8811))

#define OP_TRANSFER 0x00
#define OP_SETPERIOD 0x01
#define OP_SETPOLARITY 0x02

int main(void)
{
    uint32_t period = 10000;
    uint8_t cpol = 0, cpha = 0;
    gpo_write(make_gpo(cpol, 0));

    while (1) {
      while (!COMMAND_IN_VALID);

      uint32_t received_command = COMMAND_IN_DATA;
      uint8_t opcode = received_command >> 24;
      uint32_t data = received_command & 0x00ffffff;  // truncate to 24 bits

      if (opcode == OP_TRANSFER) {
        uint8_t result = SPI_transfer(data, period, cpol, cpha);
        while (!RESPONSE_OUT_READY);
        RESPONSE_OUT_DATA = result;
      } else if (opcode == OP_SETPERIOD) {
        period = data;
      } else if (opcode == OP_SETPOLARITY) {
        cpol = data_bit(data, 1);
        cpha = data_bit(data, 0);
        gpo_write(make_gpo(cpol, 0));
      }
    }
    return 1;
}


