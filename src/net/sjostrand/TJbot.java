package net.sjostrand;

import java.awt.Color;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.concurrent.SynchronousQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.watson.developer_cloud.http.HttpMediaType;
import com.ibm.watson.developer_cloud.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.RecognizeOptions;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechAlternative;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.SpeechResults;
import com.ibm.watson.developer_cloud.speech_to_text.v1.model.Transcript;
import com.ibm.watson.developer_cloud.speech_to_text.v1.websocket.BaseRecognizeCallback;
import com.ibm.watson.developer_cloud.text_to_speech.v1.TextToSpeech;
import com.ibm.watson.developer_cloud.text_to_speech.v1.model.Voice;
import com.ibm.watson.developer_cloud.text_to_speech.v1.util.WaveUtils;
import com.pi4j.component.servo.ServoDriver;
import com.pi4j.component.servo.ServoProvider;
import com.pi4j.component.servo.impl.RPIServoBlasterProvider;
import com.pi4j.io.gpio.RaspiPin;

/**
 * This class drives the interaction between man and machine on the TJBot, using 
 * IBM Watson services on Bluemix.
 * 
 * @author Henrik Sjöstrand
 */

public class TJbot {

	static SynchronousQueue textToConversation = new SynchronousQueue();
	static SynchronousQueue textToSpeak = new SynchronousQueue();
	static SynchronousQueue animationToPlay = new SynchronousQueue();
	static SynchronousQueue armActionsToPerform = new SynchronousQueue();

	// ActiveDialog & Activation Words
	// - if true all commands/questions/transcripts that are detected are sent to Conversation
	// - if false we wait until we have detected the activation words to go into active dialog
	static boolean activeDialog = false; 
	static String activationWords[] = { "James" };
	static String activationPhrase = "Hello"; // This is the text we send to Conversation when user has said "James"
	
	// Stop words (if you add multiple words here then they all have to be included in the stop command you say)
	static String stopWords[] = { "stop" }; // So if saying "stop James", or "James, stop", or "stop it now James" it will stop (but it rarely works...)

	// Service variables
	// Speech To Text
	static String speechToTextUsername = "*** add your credentials here ***";
	static String speechToTextPassword = "*** add your credentials here ***";
	static String speechToTextCustomizationId = "*** add your STT customization ID ***"; // If you don't have a customization ID then comment out line 383
	static SpeechToText speechToTextService = null;
	static String speechToTextLanguage = "en";
	
	// Text To Speech
	static String textToSpeechUsername = "*** add your credentials here ***";
	static String textToSpeechPassword = "*** add your credentials here ***";
	static TextToSpeech textToSpeechService = null;
	static String textToSpeechLanguage = "en";
	static String textToSpeechVoice = "Michael";

	// Conversation / Node-RED endpoint
	static String conversationURL = "http://tj-james.mybluemix.net/talk"; // *** Change to your URL ***

	// LED animation variables
	// RED=RaspiPin.GPIO_03
	// GREEN=RaspiPin.GPIO_02
	// BLUE=RaspiPin.GPIO_00
	// Set led = null; when a LED is not available, such as when running on a development machine instead of on a real TJBot
	static RGBLed led = null; // new RGBLed(RaspiPin.GPIO_03, RaspiPin.GPIO_02, RaspiPin.GPIO_00);

	static final Color COLOR_NOT_CONNECTED = Color.RED;
	static final Color COLOR_STANDBY = Color.ORANGE;
	static final Color COLOR_ACTIVEDIALOG = Color.GREEN;
	static final Color COLOR_SPEAKING = Color.WHITE;
	static final String ANIMATION_NOT_CONNECTED = "on";
	static final String ANIMATION_STANDBY = "slowPulse";
	static final String ANIMATION_ACTIVEDIALOG  = "mediumPulse";
	static final String ANIMATION_SPEAKING = "on";
	static final String ANIMATION_THINKING = "rgbPulse";

	// Speak variables
	static SourceDataLine line = null; // Our output line
	static boolean shutUp = false; // Set to true to make speaking stop
	static boolean isSpeaking = false; // Whether Speaking thread is currently speaking or not

	// Generic
	static long startTime = System.currentTimeMillis();
	static int discardedTranscriptIndex = -1; // If we're listening while speaking then this is the index of the last speechResult we have picked up, so this speechResult will be invalid

	
	// Display a message, preceded by the name of the current thread
	static void threadMessage(String message) {
		String threadName = Thread.currentThread().getName();
		threadMessage(message, threadName);
	}

	static void threadMessage(String message, String name) {
		if (name.length() < 12) name = name + "                    ".substring(0,12-name.length());
		float elapsedTime = ((float) (System.currentTimeMillis() - startTime)) / 1000;
		System.out.format("%.6g %s  %s%n", elapsedTime, name, message);
	}

	/** Returns true if the text contains all words in the words array, false if not all words found */
	static boolean containsAllWords(String text, String[] words) {
	    int numberOfWordsFound = 0;
	    for (String s: words) {
	    	if (text.indexOf(s) != -1) numberOfWordsFound++;
	    }
	    return (numberOfWordsFound == words.length);
	}
	
	
	/**
	 * Puts an animation on to the animation queue
	 * @param animation
	 */
	private static void animate(String animation) {
		if (led != null) {
			try {
				led.stopAnimation();
				Thread.sleep(50);
				led.startAnimation();
				animationToPlay.put(animation);
			} catch (InterruptedException e) {
			}
		}
	}

	/**
	 * Puts an animation on to the animation queue
	 * @param animation
	 * @param the color
	 */
	private static void animate(String animation, Color color) {
		if (led != null) {
			try {
				led.stopAnimation();
				led.setColor(color);
				Thread.sleep(50);
				led.startAnimation();
				animationToPlay.put(animation);
			} catch (InterruptedException e) {
			}
		}
	}

	private static class AnimationThread implements Runnable {

		boolean initialized = false;
		
		private void initialize() {
			try {
				led.setColorAndIntensity(Color.RED, led.getMaxIntensity());
				System.out.println("LED initialized successfully");
			} catch (Exception e) {
				System.out.println("No LED library available. No LED animations");
			} finally {
				initialized = true;
			}
		}
		
		public void run() {
			if (!initialized) initialize();
			
			try {
				for (;;) {
					String animation = animationToPlay.take().toString();
					threadMessage("Animation request: " + animation);
					if (led != null) {
						if ("slowPulse" == animation) {
							led.pulse(4000);
						} else if ("mediumPulse" == animation) {
							led.pulse(2000);
						} else if ("fastPulse" == animation) {
							led.pulse(500);
						} else if ("fastFadeOff" == animation) {
							led.fadeOffContinuously(150);
						} else if ("rainbow" == animation) {
							led.rainbow(15);
						} else if ("rgbPulse" == animation) {
							led.rgbPulse(25);
						} else if ("on" == animation) {
							led.on();
						} else if ("stop" == animation || "off" == animation) {
							led.stopAnimation();
							led.off();
						} else {
							System.out.println("Unknown animation: " + animation);
						}
					}
				}
			} catch (InterruptedException e) {
				threadMessage("I wasn't done!");
			}
		}

	}

	private static class ArmThread implements Runnable {

		final static int armMin = 50;
		final static int armMax = 250;
		final static int armCenter = (armMax - armMin) / 2 + armMin;
		
		final static int ARM_FORWARD = armMax;
		final static int ARM_BACKWARD = armMin;
		final static int ARM_VERTICAL = armCenter;
		final static int DELAY_VERTICAL = 500;
		final static int DELAY_FORWARD = 200;
		
		boolean initialized = false;
		ServoProvider servoProvider = null;
		ServoDriver servo = null;
		
		private void initialize() {
			try {
		        servoProvider = new RPIServoBlasterProvider();
		        servo = servoProvider.getServoDriver(servoProvider.getDefinedServoPins().get(0));
	        	servo.setServoPulseWidth(ARM_VERTICAL);
				System.out.println("Servo initialized successfully");
			} catch (Exception e) {
				System.out.println("No servo library available. No servo actions.");
			} finally {
				initialized = false;
			}
		}

		public void run() {
			if (!initialized) initialize();
			
			try {
				for (;;) {
					String armAction = armActionsToPerform.take().toString();
					threadMessage("ArmAction request: " + armAction);
					if (servo != null) {
						if ("wave" == armAction) {
				        	servo.setServoPulseWidth(ARM_VERTICAL);
				            Thread.sleep(DELAY_VERTICAL);
				        	servo.setServoPulseWidth(ARM_FORWARD);
				            Thread.sleep(DELAY_FORWARD);

				            servo.setServoPulseWidth(ARM_VERTICAL);
				            Thread.sleep(DELAY_VERTICAL);
				        	servo.setServoPulseWidth(ARM_FORWARD);
				            Thread.sleep(DELAY_FORWARD);

				            servo.setServoPulseWidth(ARM_VERTICAL);
						} else {
							System.out.println("Unknown arm action: " + armAction);
						}
					}
				}
			} catch (InterruptedException e) {
				threadMessage("I wasn't done!");
			}
		}

	}
		
	private static class ConversationThread implements Runnable {
		public void run() {
			try {
				for (;;) {
					threadMessage("Waiting for input text");
					String inputText = textToConversation.take().toString();
					threadMessage("Calling Conversation with: " + inputText);
					animate(ANIMATION_THINKING);
					String response = callConversation(inputText);
					threadMessage("Response: " + response);

					JsonObject responseObj = new JsonParser().parse(response).getAsJsonObject();
					String responseText = (responseObj.has("text") && !responseObj.get("text").isJsonNull() ? responseObj.get("text").getAsString() : "");
					String action = (responseObj.has("action") && !responseObj.get("action").isJsonNull() ? responseObj.get("action").getAsString() : "");
// 					threadMessage("Response: text=" + responseText + ", action=" + action);
					
					switch (action) {
					case "goodbye":
						activeDialog = false;
						break;
					case "wave":
						armActionsToPerform.put("wave");
						break;
					default:
						break;
					}
	
					textToSpeak.put(responseText);
				}
			} catch (InterruptedException e) {
				threadMessage("I wasn't done!");
			}
		}
		
		public String callConversation(String text) {
			String response = "";

			HttpClient httpClient = new DefaultHttpClient();
			try {
				HttpGet httpGetRequest = new HttpGet(conversationURL + "?text=" + URLEncoder.encode(text, "UTF-8"));
				httpGetRequest.addHeader("Content-Type", "charset=UTF-8");
				HttpResponse httpResponse = httpClient.execute(httpGetRequest);

				threadMessage(httpResponse.getStatusLine().toString());

				HttpEntity entity = httpResponse.getEntity();

				byte[] buffer = new byte[1024];
				if (entity != null) {
					InputStream inputStream = entity.getContent();
					try {
						int bytesRead = 0;
						BufferedInputStream bis = new BufferedInputStream(inputStream);
						while ((bytesRead = bis.read(buffer)) != -1) {
							String chunk = new String(buffer, 0, bytesRead);
							response += chunk;
							// System.out.println(chunk);
						}
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						try { inputStream.close(); } catch (Exception ignore) {}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			} finally {
				httpClient.getConnectionManager().shutdown();
			}

			return response;
		}
	}

	private static class ListeningThread implements Runnable {

		RecognizeOptions options = null;
		AudioInputStream audio = null;
		
		public void initialize() {
			threadMessage("Initializing Speech To Speech service");
			speechToTextService = new SpeechToText(speechToTextUsername, speechToTextPassword);

			// Signed PCM AudioFormat with 16kHz, 16 bit sample size, mono
			int sampleRate = 16000;
			AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
			DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

			if (!AudioSystem.isLineSupported(info)) {
			  System.out.println("Line not supported");
			  System.exit(0);
			}

			TargetDataLine line = null;
			try {
				line = (TargetDataLine) AudioSystem.getLine(info);
				line.open(format);
				line.start();

				audio = new AudioInputStream(line);

				options = new RecognizeOptions.Builder()
				  .continuous(true)
				  .interimResults(true)
				  .timestamps(false)
				  .wordConfidence(false)
				  .profanityFilter(false)
//				  .smartFormatting(true)
				  .customizationId(speechToTextCustomizationId)
				  .inactivityTimeout(3600) // use this to stop listening when the speaker pauses, i.e. for 5s
				  .contentType(HttpMediaType.AUDIO_RAW + "; rate=" + sampleRate)
				  .build();

			} catch (LineUnavailableException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
		
		public void close() {
			line.stop();
			line.close();
		}

		public void listen() {

			threadMessage("Listening for voice input");
			speechToTextService.recognizeUsingWebSocket(audio, options, new BaseRecognizeCallback() {
				
				public void onConnected() {
					threadMessage("--> onConnected!");
					animate(ANIMATION_STANDBY, COLOR_STANDBY);
				}

				public void onDisconnected() {
					threadMessage("--> onDisconnected!");
					close();
					initialize();
					listen();
				}

				public void onInactivityTimeout(RuntimeException runtimeException) {
					threadMessage("--> onInactivityTimeout!");
				}
				
				public void onListening() {
					threadMessage("--> onListening!");
				};

			  @Override
			  public void onTranscription(SpeechResults speechResults) {
				  
				  try {
//					  	System.out.println("SpeechResults: " + speechResults);

					  	int resultIndex = speechResults.getResultIndex();
					    List<Transcript> results = speechResults.getResults();
					    Transcript transcript = results.get(results.size()-1);
					    String text = transcript.getAlternatives().get(0).getTranscript().trim();

					    if (activeDialog) {
					    	// If we're speaking then this transcript is Watson speaking, 
					    	// so discard what we are hearing
						    if (isSpeaking)
						  		discardedTranscriptIndex = resultIndex;

						    	// else
						  		// If we are not speaking then play animation to show user we are picking up his/her voice
						  		// animate("fastPulse", Color.YELLOW);

						    // Shut up immediately if we have detected stop words
						    shutUp = containsAllWords(text, stopWords);
					    }



					    // Prepare an info message and write to console
					    String prefix = "";
					    prefix += (activeDialog ? "" : "(inactive) ");
					    prefix += (discardedTranscriptIndex == resultIndex ? "(discarded) " : "");
					    String suffix = "";
					    suffix += (shutUp ? " (STOP WORDS DETECTED - SHUTTING UP!)" : "");
					    threadMessage(prefix + text + suffix, "Listener");

					    
				    	// If we have a final transcript and it should not be discarded, go act on it
					    if (transcript.isFinal() && (resultIndex > discardedTranscriptIndex) ) {
					    	System.out.println(speechResults);

					    	SpeechAlternative alternative = transcript.getAlternatives().get(0);
					    	Double confidence = alternative.getConfidence();

					    	// If we are in standby but this are the activation words, activate and send activation phrase instead of transcribed text
						    if (!activeDialog && containsAllWords(text, activationWords)) {
						    	activeDialog = true;
						    	text = activationPhrase;
						    	confidence = 1D;
//						    	animate("fastPulse");
						    }

					    	if (activeDialog) {
//						    	threadMessage("Calling Conversation: " + text + " (" + confidence + ")", "Listen");
						    	textToConversation.put(text);
					    	}
					    }

				  } catch(InterruptedException e) {
						threadMessage("I wasn't done!");
						// closing the WebSockets underlying InputStream will close the WebSocket itself.
						line.stop();
						line.close();
						e.printStackTrace();
				  }
			  }
		  
			});
			threadMessage("After Listening for voice");
		}
		
		public void run() {
			initialize();
			listen();
		}
	}

	private static class SpeakingThread implements Runnable {

		public void run() {
			threadMessage("Initializing Text To Speech service");
			textToSpeechService = new TextToSpeech(textToSpeechUsername, textToSpeechPassword);
			
			try {
				for (;;) {
					threadMessage("Waiting for text to speak");
					String text = textToSpeak.take().toString();
					if (null != text && text.trim().length()>0) {
						speak(text);
					} else {
						isSpeaking = false;
						if (activeDialog) {
							animate(ANIMATION_ACTIVEDIALOG, COLOR_ACTIVEDIALOG);
						} else {
							animate(ANIMATION_STANDBY, COLOR_STANDBY);
						}
					}
				}
			} catch (InterruptedException e) {
				threadMessage("Tearing down");
			}
		}
		
		public void speak(String text)  {
			  
			InputStream stream = null;
			InputStream in = null;
			AudioInputStream ain = null;

//			animate("fastPulse", Color.BLUE);
			try {
				shutUp = false;
				isSpeaking = true;
				
				threadMessage("Speaking: " + text);
				// Starting an animation to show we're converting
				stream = textToSpeechService.synthesize(text, Voice.EN_MICHAEL, com.ibm.watson.developer_cloud.text_to_speech.v1.model.AudioFormat.WAV).execute();
				in = WaveUtils.reWriteWaveHeader(stream);
				
				ain = AudioSystem.getAudioInputStream(in);
				AudioFormat format = ain.getFormat();
				DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);

				// Open the line through which we will play the streaming audio.
				line = (SourceDataLine) AudioSystem.getLine(info);
				line.open(format);

				// Allocate a buffer for reading from the input stream and writing
				// to the line. Make it large enough to hold 4k audio frames.
				// Note that the SourceDataLine also has its own internal buffer.
				int framesize = format.getFrameSize();
				byte[] buffer = new byte[4 * 1024 * framesize];
				int numbytes = 0;

				boolean started = false;

				while (!shutUp) { // We'll exit the loop when we reach the end of stream
					int bytesread = ain.read(buffer, numbytes, buffer.length - numbytes);
					if (bytesread == -1) break;
					numbytes += bytesread;

					// Now that we've got some audio data to write to the line,
					// start the line, so it will play that data as we write it.
					if (!started) {
						line.start();
						started = true;
						animate(ANIMATION_SPEAKING, COLOR_SPEAKING);
					}

					int bytestowrite = (numbytes / framesize) * framesize;
					line.write(buffer, 0, bytestowrite);
					int remaining = numbytes - bytestowrite;
					if (remaining > 0)
						System.arraycopy(buffer, bytestowrite, buffer, 0, remaining);
					numbytes = remaining;
				}
				
				if (!shutUp) {
					// Now block until all buffered sound finishes playing.
					// threadMessage("Draining line (continues until end)");
					line.drain();
				} else {
					// Remove all audio data
					threadMessage("Flushing line (stops immediately)");
					line.flush();
				}
				
			} catch (Exception e) {
				e.printStackTrace();
				
			} finally {
				threadMessage("Cleaning up");
				line.flush();
				line.stop();
				if (line != null)
					line.close();
				close(ain);
				close(stream);
				close(in);

				isSpeaking = false;
				if (activeDialog) {
					animate(ANIMATION_ACTIVEDIALOG, COLOR_ACTIVEDIALOG);
				} else {
					animate(ANIMATION_STANDBY, COLOR_STANDBY);
				}
			}
		}
		
		public void close(InputStream s) {
			if (s != null) {
				try {
					s.close();
				} catch (IOException hidden) {
				}
			}
			
		}
	}

	
	public static void main(String args[]) throws InterruptedException {

		// Delay, in milliseconds before we interrupt MessageLoop thread (20s).
		long patience = 60 * 60 * 1000; // 1h 30 * 1000; // 30 sec, 

		threadMessage("Starting up the SpeakingThread");
		Thread speakingThread = new Thread(new SpeakingThread());
		speakingThread.setName("Speaker");
		speakingThread.start();

		threadMessage("Starting up the AnimationThread");
		Thread animationThread = new Thread(new AnimationThread());
		animationThread.setName("Animator");
		animationThread.start();

		threadMessage("Starting up the ArmThread");
		Thread armThread = new Thread(new ArmThread());
		armThread.setName("Arm");
		armThread.start();

		threadMessage("Starting up the ConversationThread");
		Thread conversationThread = new Thread(new ConversationThread());
		conversationThread.setName("Conversation");
		conversationThread.start();

		threadMessage("Starting up the ListenThread");
		Thread listeningThread = new Thread(new ListeningThread());
		listeningThread.setName("Listener");
 		listeningThread.start();
		
 		animate(ANIMATION_NOT_CONNECTED, COLOR_NOT_CONNECTED);

//		animate("rainbow");
//		animate("rgbPulse");
		
		threadMessage("Waiting for fun to happen during " + (patience / 1000) + " sec");
		Thread.sleep(patience);

		threadMessage("Shutting down");

		animate("off");
		
		threadMessage("Terminating Listening Thread");
		listeningThread.interrupt();
		listeningThread.join();

		threadMessage("Terminating Speaking Thread");
		speakingThread.interrupt();
		speakingThread.join();

		threadMessage("Terminating Conversation Thread");
		conversationThread.interrupt();
		conversationThread.join();

		threadMessage("Terminating Arm Thread");
		armThread.interrupt();
		armThread.join();

		threadMessage("Terminating Animate Thread");
		animationThread.interrupt();
		animationThread.join();

		
		threadMessage("All threads terminated. Exiting.");

	}
}