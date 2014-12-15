#include "flexpret_timing.h"
#include "flexpret_io.h"
#include <stdint.h>
#include <stdio.h>

#define PERIOD 10000 
unsigned int clk = 1;

uint8_t SPI_transfer(uint8_t write_byte, uint8_t cpol, uint8_t cpha)
{
    uint32_t clk;

    uint8_t result = 0;

    uint8_t clk_bit = cpol;
    uint8_t phase = cpha;

    uint8_t out_mask_bit = 0x80;
    uint8_t out_data_bit = !((write_byte & out_mask_bit) == 0);

    if (!cpha) {
      // if cpha == 0, do a dummy half-cycle
      uint8_t to_write = ((clk_bit << 2) | out_data_bit);
      gpo_write(to_write);
      periodic_delay(&clk, PERIOD/2);
    }

    while (out_mask_bit) {
      if (!phase) {    // phase = 0, capture
        result = result << 1;
        if (gpi_read() & 0x01) {
          result |= 0x01;    
        }
        out_mask_bit = out_mask_bit >> 1;
      } else {    // phase = 1, new data
        out_data_bit = !((write_byte & out_mask_bit) == 0);
      }

      clk_bit = !clk_bit;
      phase = !phase;

      uint8_t to_write = ((clk_bit << 2) | out_data_bit);
      gpo_write(to_write);
      periodic_delay(&clk, PERIOD/2);
    }

    // ensure clock line returns to idle polarity
    if (clk_bit != cpol) {
      uint8_t to_write = ((cpol << 2) | out_data_bit);
      gpo_write(to_write);
      periodic_delay(&clk, PERIOD/2);
    }

    return result;
}

int main(void)
{
    uint8_t result = SPI_transfer(0x4a, 0, 0);
    debug_string(itoa_hex(result));
    debug_string("\n");
    return 1;
}


