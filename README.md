The TJBot is an IBM project that makes use of the IBM Watson cognitive services on IBM Bluemix cloud platform in a fun way. For information about the TJBot project see https://ibmtjbot.github.io/

This repo is my implementation of my TJBot, called James.

I did have some issues with the Raspberry Pi 3 together with the LED, servo, microphone and speaker that the TJBot uses. I wanted to animate the LED and exercise the servo and at the same time use the microphone to record and speaker to play. Because I wanted more concurrency than what I easily could achieve with the normal TJBot setup I have modified my setup quite substantially from the one at the IBM TJBot site above.

First I have switched from using Node.js on the client (the TJBot itself) to using a multithreaded Java application I wrote. Then in order to easily extend the "intelligence" of the TJBot with new capabilites (use cases) I created a Node-RED flow, hosted on Bluemix.

The Java client is responsible for recording audio, send the audio stream to Watson Speech To Text service on Bluemix, retrieve the transcribed text, check for activation words (I use "James"), and when activated send the text to the Node-RED flow. The flow receives the text (commands, questions etc) and sends to the Watson Conversation service, which drives the man machine dialog. Until the activation word is heard the client just listens but never sends what it hears to Node-RED. After activated everything is sent to the Node-RED flow and forwarded to Conversation. To get maximum responsiveness the streaming capabilities of the Watson Speech To Text service is used, so it transcribes the audio into text in real-time.

The output from Conversation is examined in the Node-RED flow and depending on the output different actions are taken, such as tell a joke, check bus time tables, check weather etc.

Secondly, I use a different type of LED that I ordered from www.ebay.co.uk/itm/RGB-LED-5810-mm-DiffusedWater-Clear-Tri-Colour-4-Pin-Common-AnodeCathode-/172615621604 (it's available from many other sources as well). This is a less intelligent LED than the one used in the normal TJBot. But the advantage is it does not require the PWM on the Raspberry to drive it, so the PWM is freed up for other uses. I also found that the PWM conflicted with the speaker so getting rid of that dependency was a huge plus.

The LED I ordered was Common Cathode, Diffused, 8mm. I (... believe I) wired the Red pin to Raspberry Pi's GPIO 3, Green to GPIO 2 and Blue to GPIO 0, and common cathode to Raspberry Pi's ground. You can use different wiring as long as you change the configuration in the Java client.


To get started with my version of TJBot you first need to build a standard TJBot, but replace the LED with the one I use. Then you need to setup the following four services on Bluemix:
- Watson Conversation
- Watson Speech To Text
 -Watson Test To Speech
- Weather Company Data

You also need to create a Node-RED instance to hold the Node-RED flow (create a Node-RED starter on Bluemix).

In Watson Conversation import the WatsonConversation_workspace.json file.
In Node-RED import the NodeRED_flow.json file.

Then in the Node-RED flow update the following:

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

Apache HTTP Components (for making outbound HTTP requests) - https://hc.apache.org/downloads.cgi
Pi4J (for communicating with the GPIO and drive LEDs)- http://pi4j.com/download.html
Watson Java SDK - https://github.com/watson-developer-cloud/java-sdk/releases

Check the .classpath for the exact filenames and versions that I have used in case you run into any issues.

When you have built the Java application export it as tjbot.jar and ensure that the TJbot class is marked as the Jar's executable class.


To drive the servo you need to use a PWM output, but since I had issues with static noise in the speaker when using the Raspberry Pi's hardware PWM and exercising the servo at the same time as playing back audio, I had to use a software PWM instead. This is available as the ServoBlaster part of the PiBits project - https://github.com/richardghirst/PiBits/tree/master/ServoBlaster. You need to download and build the servod servo daemon and install to your TJBot.


To run the TJBot java application and set up the servod listener I use the following shell script:

sudo ./servod --p1pins=7,0,0,0,0,0,0,0
sudo java -jar tjbot.jar
sudo killall servod

