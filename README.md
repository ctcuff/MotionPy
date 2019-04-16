
# MotionPy
A motion detection system for a Raspberry Pi 3 along with an Android app to control it. 

### How does it work? 
There is a distance sensor attatched to the Raspberry Pi that reads a fixed distance at a set interval, for example, 120 cm every 0.02 seconds. When that distance gets lower than a certain threshold (40 cm in this project), the Pi captures an image, uploads it to Firebase Storage, and then uses Firebase Cloud Messaging to send a notification containing a timestamp and image url to your phone. In order for the phone to send messages to the Raspberry Pi, I set up a Heroku server running Flask to listen for web requests. The route that listens for requests is protcted by a server key that you'll need to generate (to prevent random access). When it gets a command, that command is sent to the Raspberry Pi.

### Required parts
1) [Raspberry Pi 3](https://www.adafruit.com/product/3775?gclid=Cj0KCQjw7sDlBRC9ARIsAD-pDFraBQQclP4U5d4Z5qLc5kEgVZE71GuaBx1SW1VR0xpsSzxjjSjf1ycaAuubEALw_wcB)
2) [5v power adapter with USB Type-C cable](https://www.amazon.com/s?k=5v+2.5a+adapter&ref=nb_sb_noss_1)
3) [HC-SR04 Distance sensor](https://www.amazon.com/gp/product/B01GNEHJNC/ref=ppx_yo_dt_b_asin_title_o06_s00?ie=UTF8&psc=1)
4) [5.5×8.2×0.85cm Mini breadboard](https://www.amazon.com/gp/product/B0135IQ0ZC/ref=ppx_yo_dt_b_asin_title_o05_s00?ie=UTF8&psc=1)
5) [3 1k ohm resistors](https://www.amazon.com/gp/product/B07HDDWFDD/ref=ppx_yo_dt_b_asin_title_o05_s01?ie=UTF8&psc=1)
6) [Camera module](https://www.amazon.com/gp/product/B012V1HEP4/ref=ppx_yo_dt_b_asin_title_o03_s00?ie=UTF8&psc=1) (_Note: you can use any camera but this project was build aroundthis specific camera_)
7) [M/F Jumper wires](https://www.amazon.com/gp/product/B01GNEHJNC/ref=ppx_yo_dt_b_asin_title_o06_s00?ie=UTF8&psc=1) (_Optional: The sensor comes with wires but extra jumper wires means you don'thave to attach the sensor to the breadboard directly_)
8) [TP-Link 150Mbps High Gain Wireless USB Adapter](https://www.amazon.com/TP-Link-TL-WN722N-Wireless-network-Adapter/dp/B002SZEOLG/ref=asc_df_B002SZEOLG/?tag=hyprod-20&linkCode=df0&hvadid=312727440900&hvpos=1o1&hvnetw=g&hvrand=517085771556718726&hvpone=&hvptwo=&hvqmt=&hvdev=c&hvdvcmdl=&hvlocint=&hvlocphy=9011804&hvtargid=pla-318320045266&psc=1) (_Optional: Allows you to use VNC to view the Pi's screen remotely while it's also connected to WiFi, [see here for setup](https://www.raspberrypi.org/documentation/remote-access/vnc/)_)

### Setting it up
1) Before setting up the Raspberry Pi, head over to [Firebase](https://firebase.google.com/) and create a new app. In order to be able to send Firebase messages, you'll need a [device registration token](https://firebase.google.com/docs/cloud-messaging/android/client).
2) Set up a quick Heroku site and take note of the url.
3) This part might depend on how you want to set up the sensor. Take the 4 jumper wires and connect them to the following GPIO pins on the Pi: 5v, Ground, GPIO 4, GPIO 18. ([See this video for a visual](https://www.youtube.com/watch?v=kqJ8WYQu68w&)). Again the setup depends but the end result should look something like this:

<img src="https://github.com/ctcuff/MotionPy/blob/master/images/materials.jpg" width="420"></img>

4) If everything is set up correctly, running [raspberrypi/check_distance.py](https://github.com/ctcuff/MotionPy/blob/master/raspberrypi/check_distance.py) should give you a valid distance.
5) The last step is to create a few config files I've excluded from this repo. The first one should be in /app/.../motionpy/Config.kt:

```kotlin
// Config.kt
class ServerConfig {
    companion object {
        /* *
        * This is generated with the Python uuid lib. In this project,
        * I simply used str(uuid4()), but any random key will do.
        */
        const val SERVER_KEY = "Your server key here"
        /**
        * Here, the "/control" route is what receives for commands.
        * See: https://github.com/ctcuff/MotionPy/blob/master/server/app.py#L43
        */
        const val URL = "[Your Heroku URL here]/control"
    }
}
```
The next one should be in raspberrypi/config.py
```python
# Find this in Firebase project settings under the cloud messaging tab.
API_KEY = 'Firebase Legacy Server key'
# This is the name of your Firebase App
PROJECT_ID = 'Your project if here'
# This can be found with step 1 of the setup.
REGISTRATION_ID = 'Device regidtration id'
# This is the same url thats in Config.kt (minus the /control route)
SERVER_URL = 'Your Heroku server URL'
```
Lastly, this config should bein server/config.py
```python
# You can generate this with uuid4() from the uuid lib. For example:
# >>> from uuid import uuid4
# >>> print(uuid4())
# c0958cb9-7b24-448b-a555-5f89c0c312c4
# Note that this should be the same key in Config.kt
SERVER_KEY = 'Key here'
```
7) Now that the config is set up, the last step is to place a 2 Firebase json files in the project. You'll `google-services.json` after creating a new Firebase app or you can find a link to download it under the project settings tab. Place this file under app/app. The next json file is `serviceAccountKey.json` altough I've renamed it to `firebase-admin.json`. This can be found under Settings > Service accounts > Generate new private key. Place this file in the raspberrypi/ directory.

### The end result
This took quite a bit of time to get up and running but here's the end result:

Here's what the output looks like when the sensor is running on the Raspberry Pi:

<img src="https://github.com/ctcuff/MotionPy/blob/master/images/sensor-running.png" width="550"></img>

Here's what happens when movement is detected:

<img src="https://github.com/ctcuff/MotionPy/blob/master/images/app-movement.gif" width="250"></img>

