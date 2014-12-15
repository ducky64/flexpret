#include "flexpret_timing.h"
#include "flexpret_io.h"
#include <stdint.h>
#include <stdio.h>

#define PERIOD 10000 
unsigned int clk = 1;

uint8_t SPI_transfer(uint8_t write_byte, uint8_t cpol, uint8_t cpha)
{

    //gpo_clear(0x10000000);

    uint8_t result = 0;
    uint8_t bit = 0x80;
    uint8_t clk_bit = cpol;

    for (bit = 0x80; bit; bit >>= 1) {
	uint8_t data_bit = 0;
	uint8_t to_write = 0;

	if (!cpha) 
	{
		if (write_byte & bit) {
		    data_bit = 1;
		}
		to_write = ((clk_bit << 2) | data_bit );
		gpo_write(to_write);
	}
	else 
	{
		if (gpi_read()) 
		{
		     result |= bit;	
		}
	}

	periodic_delay(&clk, PERIOD/2);
	clk_bit = cpol ? 0 : 1;
        
	if (cpha) 
	{
		if (write_byte & bit) {
		    data_bit = 1;
		}
		to_write = ((clk_bit << 2) | data_bit );
		gpo_write(to_write);
	}
	else 
	{
		if (gpi_read()) 
		{
		     result |= bit;	
		}
	}

	to_write = ((clk_bit << 2) | data_bit );
	gpo_write(to_write);
	periodic_delay(&clk, PERIOD/2);
	clk_bit = cpol;
	result = result << 1;
    }

    //gpo_set(0x10000000);

    return result;
}

int main(void)
{
    uint8_t result = SPI_transfer(0x4a, 1, 1);
    debug_string(itoa_hex(result));
    debug_string("\n");
    return 1;
}


