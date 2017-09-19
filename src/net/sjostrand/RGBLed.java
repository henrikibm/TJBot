package net.sjostrand;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.wiringpi.SoftPwm;
import java.awt.Color;

/**
 * This class drives a RGB LED using software PWM to dim each component according
 * to the given color. Most RGB LEDs are simply connected to one GPIO pin per color
 * channel.
 * 
 * @author Florian Frankenberger, enhanced by Henrik Sjöstrand
 */
public class RGBLed {
    
    private final Pin redPin;
    private final Pin greenPin;
    private final Pin bluePin;
    private Color color = Color.BLACK;
    private float intensity = 0;
    
    private boolean animate = true;
    private int maxIntensity = 100;

    /**
     * constructs a new RGBLed using the given pinLayout to control the
     * LED
     * 
     * @param redPin red pin on GPIO
     * @param greenPin green pin on GPIO
     * @param bluePin blue pin on GPIO
     */
    public RGBLed(Pin redPin, Pin greenPin, Pin bluePin) {
        this.redPin = redPin;
        this.greenPin = greenPin;
        this.bluePin = bluePin;

        final GpioController gpio = GpioFactory.getInstance();

        final GpioPinDigitalOutput ledRed = gpio.provisionDigitalOutputPin(redPin);
        final GpioPinDigitalOutput ledGreen = gpio.provisionDigitalOutputPin(greenPin);
        final GpioPinDigitalOutput ledBlue = gpio.provisionDigitalOutputPin(bluePin);

        ledRed.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);
        ledGreen.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);
        ledBlue.setShutdownOptions(true, PinState.LOW, PinPullResistance.OFF);

        SoftPwm.softPwmCreate(redPin.getAddress(), 0, maxIntensity);
        SoftPwm.softPwmCreate(greenPin.getAddress(), 0, maxIntensity);
        SoftPwm.softPwmCreate(bluePin.getAddress(), 0, maxIntensity);
        
        off();
    }

    /**
     * Enables animation to run. Call before any animation.
     */
    public void startAnimation() {
    	this.animate = true;
//    	System.out.println("StartAnimation");
    }

    /**
     * Immediately stops any animations (pulse, fade, rainbow etc.)
     * and leaves LED in its current state
     */
    public void stopAnimation() {
    	this.animate = false;
//    	System.out.println("StopAnimation");
    }

    /**
     * Sets the RGB LED to display the given color with given intensity
     * @param color the color to display
     * @param the intensity (0-100), 0=off, 100=max
     */
    public void setColorAndIntensity(Color color, float intensity) {
        final float[] colors = color.getRGBColorComponents(null);
        if (intensity > maxIntensity)
        	intensity = maxIntensity;
        if (intensity < 0)
        	intensity = 0;
        this.color = color;
        this.intensity = intensity;
        int redValue = Math.round(colors[0] * intensity);
        int greenValue = Math.round(colors[1] * intensity);
        int blueValue = Math.round(colors[2] * intensity);
/*
        String x = "SetColor: ";
        x += "R=" + colors[0] + " G=" + colors[1] + " B=" + colors[2];
        x += "  " + redValue + " " + greenValue + " " + blueValue;
        x += "  " + intensity;
        System.out.println(x);
*/        
        SoftPwm.softPwmWrite(redPin.getAddress(), redValue);
        SoftPwm.softPwmWrite(greenPin.getAddress(), greenValue);
        SoftPwm.softPwmWrite(bluePin.getAddress(), blueValue);
    }

    /**
     * Sets the RGB LED to display the given color with existing intensity
     * (so only changes color)
     * 
     * @param color the color to display
     */
    public void setColor(Color color) {
    	setColorAndIntensity(color, intensity);
    }

    /**
     * Sets the RGB LED to display using current color but new intensity
     * (so only changes intensity)
     * 
     * @param intensity the intensity
     */
    public void setIntensity(float intensity) {
    	setColorAndIntensity(color, intensity);
    }

    /**
     * disables the RGB LED by cutting the power. Note that this is the same as displayColor(Color.BLACK).
     * It is also a good idea to call this before the application exits as otherwise the RGB LED will stay
     * in its last state even if the application terminates.
     */
    public final void off() {
        setIntensity(0);
    }

    /**
     * Turns on the RGB LED with current color and 100% intensity
     */
    public final void on() {
        setIntensity(maxIntensity);
    }

    /**
     * returns the currently displayed color of the RGB LED.
     * 
     * @return the color displayed
     */
    public Color getColor() {
        return color;
    }

    /**
     * returns the current intensity of the RGB LED.
     * 
     * @return the intensity
     */
    public float getIntensity() {
        return intensity;
    }

    /**
     * returns the max intensity that can be set (100%)
     * 
     * @return the intensity
     */
    public int getMaxIntensity() {
        return (int) intensity;
    }

    /**
     * Pulse LED - fade On and Off
     * @param duration the number of milliseconds for the whole pulse ( Off -> On -> Off)
     */
    public void pulse(int duration) {
		while (animate) {
			fadeOn(duration / 2);
			fadeOff(duration / 2);
		}
    }

    /**
     * FadeOffContinuously
     * @param duration the number of milliseconds for the whole pulse ( Off -> On -> Off)
     */
    public void fadeOffContinuously(int duration) {
		while (animate) {
			fadeOff(duration);
		}
    }

    /**
     * Fades the led ON from 0 to 100% during duration ms
     * @param duration the duration in ms
     * @param delay the delay between intensity changes
     */
    public void fadeOn(int duration, int delay) {
    	float steps = (float) delay / (float) duration * maxIntensity;
//    	float dd = (float) duration / (float) delay;
//    	float corr = dd / (dd + 1);
// 		System.out.println("FadeOn: duration=" + duration + ", delay=" + delay + " -> steps=" + steps);
		float i = 0;
		try {
			while (animate && i <= maxIntensity) {
				setIntensity(i);
				Thread.sleep(delay);
				i += steps;
			}
		} catch (InterruptedException e) {
		}
		// if (!animate) System.out.println("FadeOn aborted");
    }

    /**
     * Fades the led ON from 0 to 100% during duration ms
     * @param duration the duration in ms
     */
    public void fadeOn(int duration) {
    	fadeOn(duration, 50);
    }

    /**
     * Fades the led OFF from 100% to 0 during duration ms
     * @param duration the duration in ms
     * @param delay the delay between intensity changes
     */
    public void fadeOff(int duration, int delay) {
    	float steps = (float) delay / (float) duration * maxIntensity;
//    	float dd = (float) duration / (float) delay;
//    	float corr = dd / (dd + 1);
// 		System.out.println("FadeOff: duration=" + duration + ", delay=" + delay + " -> steps=" + steps);
		float i = maxIntensity;
		try {
			while (animate && i >= 0) {
				setIntensity(i);
				Thread.sleep(delay);
				i -= steps;
			}
		} catch (InterruptedException e) {
		}
		// if (!animate) System.out.println("FadeOff aborted");
    }

    /**
     * Fades the led Off from 100 to 0% during duration ms
     * @param duration the duration in ms
     */
    public void fadeOff(int duration) {
    	fadeOff(duration, 50);
    }
    
    /**
     * Circulates between all the colors of the rainbow
     * @param delay the delay between color changes
     */
    public void rainbow(int delay) {
    	
    	float saturation = 1.0F; // 1.0F
    	float brightness = 0.25F; // 0.25F
    	float hue = 0.0F; // iterate to 1.0F;
    	float increment = 0.01F;
    	Color c = null;
		try {
			while (animate) {
				c = Color.getHSBColor(hue,  saturation,  brightness);
				setColor(c);
				Thread.sleep(delay);
				hue += increment;
				if (hue > 1.0F) hue = 0.0F;
			}
		} catch (InterruptedException e) {
		}
		if (!animate) System.out.println("Rainbow aborted");
    }

    /**
     * Loops over R, G, B
     * @param delay the delay between color changes
     */
    public void rgbPulse(int delay) {
    	
    	Color c = null;
    	boolean r = true;
    	boolean g = false;
    	boolean b = false;
    	boolean increment = true;
    	int min = 10;
    	int max = 255;
    	int steps = 20;
    	int i = min;
		try {
			while (animate) {
				if (r) c = new Color(i, 0, 0);
				if (g) c = new Color(0, i, 0);
				if (b) c = new Color(0, 0, i);
				setColor(c);
				Thread.sleep(delay);
				if (increment)
					i = i + steps;
				else 
					i = i - steps;
				if (i <= min) {
					i = min;
					increment = !increment;
				}
				if (i >= max) {
					i = max;
					increment = !increment;
				}
				if (i == min) {
					if (r) {
						r = false;
						g = true;
					} else if (g) {
						g = false;
						b = true;
					} else if (b) {
						b = false;
						r = true;
					}
				}
				
			}
		} catch (InterruptedException e) {
		}
//		if (!animate) System.out.println("RGBPulse aborted");
    }

}
