The TJBot is an IBM project that makes use of the IBM Watson cognitive services on IBM Bluemix cloud platform in a fun way. For information about the TJBot project see https://ibmtjbot.github.io/

This repo is my implementation of my TJBot, called James. See the video here https://www.youtube.com/watch?v=nbehgxPxypA

Originally I built a standard TJBot using the example recepies. I did however have some issues with the Raspberry Pi 3 together with the LED, servo, microphone and speaker that the TJBot uses. I also wanted a very responsive dialog and be able to animate the LED and exercise the servo and at the same time use the microphone to record and speaker to play. So because I wanted more concurrency than what I easily could achieve with the normal TJBot setup I have modified my setup quite substantially from the one at the IBM TJBot site above.

First I have switched from using Node.js on the client (the TJBot itself) to using a multithreaded Java application I wrote. Then in order to easily extend the "intelligence" of the TJBot with new capabilites (use cases) I created a Node-RED flow, hosted on Bluemix.

The Java client is responsible for recording audio, send the audio stream to Watson Speech To Text service on Bluemix, retrieve the transcribed text, check for activation words (I use "James"), and when activated send the text to the Node-RED flow. The flow receives the text (commands, questions etc) and sends to the Watson Conversation service, which drives the man machine dialog. Until the activation word is detected the client just listens but never sends what it hears to Node-RED. After activated everything is sent to the Node-RED flow and forwarded to Conversation. To get maximum responsiveness the streaming capabilities of the Watson Speech To Text service is used, so it transcribes the audio into text and returns to the TJBot in real-time.

The output from Conversation is examined in the Node-RED flow and depending on the output different actions are taken, such as tell a joke, check bus time tables, check weather etc.

Secondly, I use a different type of LED that I ordered from www.ebay.co.uk/itm/RGB-LED-5810-mm-DiffusedWater-Clear-Tri-Colour-4-Pin-Common-AnodeCathode-/172615621604 (it's available from many other sources as well). This is a less intelligent LED than the one used in the normal TJBot. But the advantage is it does not require the Pulse Width Modulation (PWM) on the Raspberry to drive it, so the PWM is freed up for other uses. I also found that the PWM caused static noise in the speaker so getting rid of that dependency was a huge plus.

The LED I use was Common Cathode, Diffused, 8mm. I wired (I believe...) the Red pin to Raspberry Pi's GPIO 3, Green to GPIO 2 and Blue to GPIO 0, and common cathode to Raspberry Pi's ground. You can use different wiring as long as you change the configuration in the Java client.

Once you have assembled the hardware you should verify that the microphone and speaker works. I had plenty of problems here and spent a lot of time troubleshooting. Here is what I had to do:

1) Disable some modules in the Ubuntu Linux by adding a /etc/modprobe.d/tjbot-blacklist-snd.conf with the following content:
blacklist snd_bcm2835
blacklist snd_pcm
blacklist snd_timer
blacklist snd_pcsp
blacklist snd

Reboot and log in again

2) Check what  sudo cat /proc/asound/cards  returns. On my hardware it says:

 0 [Device         ]: USB-Audio - USB2.0 Device
                      Generic USB2.0 Device at usb-3f980000.usb-1.3, full speed
 
 1 [AK5371         ]: USB-Audio - AK5371
                      AKM AK5371 at usb-3f980000.usb-1.2, full speed


Device 0 is the speaker and device 1 is the microphone (I use another microphone than the very small USB version, so your name may be different).

To ensure that the Java application can record and play back audio you need to configure the asound.conf file.
Edit (sudo) /etc/asound.conf and ensure it contains:

pcm.!default {
    type plug slave { pcm "hw:0,0" }
}

ctl.!default {
    type hw card 0
}

XXXpcm.record {
    type plug slave { pcm "hw:2,0" }
}


This configuration matches my hardware and the order my microphone and speaker is plugged in. You may need to modify yours to match your hardware, and with what the cat /proc/asound/cards gives.

After reboot you can test that microphone and speaker works by:
sudo arecord --device=hw:2,0 -r 16000 -f S16_LE test.wav
aplay test.wav

You should also run alsamixer to adjust your microphone's and speaker's volumes.

If the arecord and aplay commands don't work, audio in the TJBot may actually still work, so don't get stuck here.





To get started with my version of TJBot you first need to build a standard TJBot, but replace the LED with the one I use and perform above configurations.
Then you need to setup the following four services on Bluemix:
- Watson Conversation
- Watson Speech To Text
- Watson Text To Speech
- Weather Company Data

You also need to create a Node-RED instance to hold the Node-RED flow (create a Node-RED starter on Bluemix).

In Watson Conversation import the WatsonConversation_workspace.json file.
In Node-RED import the NodeRED_flow.json file.

Then in the Node-RED flow update the following with your own credentials:

Main flow:
* Update Conversation node with your credentials (username/password) and your Conversation workspace id. Ensure “Save context" remains checked.

Bus subflow:
Go to Västtrafik’s API portal and sign up and register your application - https://developer.vasttrafik.se/portal/

Then in the Bus subflow open the Get Token HTTP request component
* Enable basic authentication
* Add your Västtrafik userid/password

Weather subflow:
In the Get Token HTTP request component:
* Enable basic authentication
* Add your Bluemix Weather API credentials


I use Eclipse to build the Java code, which runs *on* the TJBot. It requires the following external libraries that need to be added to the classpath and buildpath:

- Apache HTTP Components (for making outbound HTTP requests) - https://hc.apache.org/downloads.cgi
- Pi4J (for communicating with the GPIO and drive LEDs)- http://pi4j.com/download.html
- Watson Java SDK - https://github.com/watson-developer-cloud/java-sdk/releases

Check the .classpath for the exact filenames and versions that I have used in case you run into any issues.

The main class is the net/sjostrand/TJbot.java file. Lines 70-84 needs to be updated with the credentials for your Bluemix Speech To Text and Text To Speech services, and the URL to your Node-RED flow.

Line 91 controls the configuration of the LED. If you don't have the same LED as I and don't care about it, set it to null.


When you have built the Java application export it as tjbot.jar and ensure that the TJbot class is marked as the Jar's executable class.


To drive the servo you need to use a PWM output, but since I had issues with static noise in the speaker when using the Raspberry Pi's hardware PWM and exercising the servo at the same time as playing back audio, I had to use a software PWM instead. This is available as the ServoBlaster part of the PiBits project - https://github.com/richardghirst/PiBits/tree/master/ServoBlaster. You need to download and build the servod servo daemon and install to your TJBot.


To run the TJBot java application and set up the servod listener before and tear down after, I use the following shell script:

sudo ./servod --p1pins=7,0,0,0,0,0,0,0
sudo java -jar tjbot.jar
sudo killall servod



Happy TJBot'ing !

