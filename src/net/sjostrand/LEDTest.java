package net.sjostrand;

import java.awt.Color;

import com.pi4j.io.gpio.RaspiPin;

/**
 * This class performs simple tests of the LED.
 * 
 * @author Henrik Sjöstrand
 */

public class LEDTest {

	public static void main(String[] args) throws InterruptedException {
		// TODO Auto-generated method stub

		RGBLed led = new RGBLed(RaspiPin.GPIO_01, RaspiPin.GPIO_00, RaspiPin.GPIO_03);

		led.setColor(Color.GREEN);
		
		/*
		System.out.println("Blink Green");
		for (int i = 0; i < 10; i++) {
			led.on();
			Thread.sleep(delay);

			led.off();
			Thread.sleep(delay);
		}
		*/

		for (;;) {
			System.out.println("Pulse 3000ms");
			long startTime = System.currentTimeMillis();
			for (int i = 0; i < 5; i++) {
				led.pulse(3000);
			}
			System.out.println(System.currentTimeMillis() - startTime);

			System.out.println("FadeOff 200ms");
			startTime = System.currentTimeMillis();
			for (int i = 0; i < 10; i++) {
				led.fadeOff(200);;
			}
			System.out.println(System.currentTimeMillis() - startTime);

		}
	}
	

}
