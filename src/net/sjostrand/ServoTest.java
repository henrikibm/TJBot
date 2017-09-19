package net.sjostrand;

import com.pi4j.component.servo.ServoDriver;
import com.pi4j.component.servo.ServoProvider;
import com.pi4j.component.servo.impl.RPIServoBlasterProvider;

/**
 * This class performs simple tests of the Pi4J servo driver.
 * 
 * @author Henrik Sjöstrand
 */

public class ServoTest {

	final static int min = 50;
	final static int max = 250;
	final static int center = (max - min) / 2 + min;
	
	final static int ARM_FORWARD = max;
	final static int ARM_BACKWARD = min;
	final static int ARM_VERTICAL = center;
	final static int DELAY_VERTICAL = 500;
	final static int DELAY_FORWARD = 200;
	
    public static void main(String[] args) throws Exception {
        ServoProvider servoProvider = new RPIServoBlasterProvider();
        ServoDriver servo = servoProvider.getServoDriver(servoProvider.getDefinedServoPins().get(0));
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < 120000) { // 2 minutes
/*          
			for (int i = begin; i < end; i++) {
                servo.setServoPulseWidth(i); // Set raw value for this servo driver - 50 to 195
                Thread.sleep(delay);
            }
*/
        	servo.setServoPulseWidth(ARM_VERTICAL);
            Thread.sleep(DELAY_VERTICAL);
/*
            for (int i = end; i > begin; i--) {
                servo.setServoPulseWidth(i); // Set raw value for this servo driver - 50 to 195
                Thread.sleep(delay);
            }
*/
        	servo.setServoPulseWidth(ARM_FORWARD);
            Thread.sleep(DELAY_FORWARD);
        }
        System.out.println("Exiting RPIServoBlasterExample");
    }
}
