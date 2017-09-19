#TJBot

The TJBot is an IBM project that makes use of the IBM Watson cognitive services on IBM Bluemix cloud platform in fun ways. For information about the TJBot project see https://ibmtjbot.github.io/

This repo is my implementation of my TJBot, called James.

I did have some issues with the Raspberry Pi 3 together with the LED, servo, microphone and speaker that the TJBot uses. I wanted to animate the LED and exercise the servo and at the same time use the microphone to record and speaker to play. Because I wanted more concurrency then what I easily could achieve with the normal TJBot setup I have modified my setup quite substantially from the one at the IBM TJBot site above.

First I have switched from using Node.js on the client (the TJBot itself) to using a multithreaded Java application I wrote. Then in order to easily extend the "intelligence" of the system with new capabilites I created a Node-RED flow, hosted on Bluemix.

The Java client is responsible for recording audio, send this to Watson Speech To Text service on Bluemix, check for activation words (I use "James"), and when activated send the text to the Node-RED flow. The flow receives the text (commands, questions etc) and sends to the Watson Conversation service, which drives the man machine dialog. Until the activation word is heard the client just listens but never sends what it hears. After activated everything which is heard is sent to the Node-RED flow and forwarded to Conversation.

The output from Conversation is checked in the Node-RED flow and depending on the output different actions are taken, such as tell a joke, check bus time tables, check weather etc.

Secondly, I use a different type of LED that I ordered from here www.ebay.co.uk/itm/RGB-LED-5810-mm-DiffusedWater-Clear-Tri-Colour-4-Pin-Common-AnodeCathode-/172615621604 (it's available from many other sources as well). This is a less intelligent LED then the one used in the normal TJBot. But the advantage is it does not require the PWM on the Raspberry to drive it, so it is freed up for other uses.

The LED I ordered was Common Cathode, Diffused, 8mm, 10pack. I wired the Red pin to Raspberry Pi's GPIO 3, Green to GPIO 2 and Blue to GPIO 0, and common cathode to Raspberry Pi's ground. You can use different wiring as long as you change the configuration in the Java client.