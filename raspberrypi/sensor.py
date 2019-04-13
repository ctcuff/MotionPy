import RPi.GPIO as GPIO
import sys
import os
from picamera import PiCamera
from time import time, sleep
from datetime import datetime
from notify import send_push_notification, dropbox_upload, database_upload


class Sensor:
    TRIG = 4
    ECHO = 18
    # The distance in centimeters that counts as motion.
    THRESHOLD = 40
    # The maximum amount of pictures that can be saved.
    PICTURE_LIMIT = 10
    # The number of seconds between sensor readings before the
    # program stops itself.
    MAX_PING_TIMEOUT = 30
    # The amount of time in seconds to wait before taking the next picture.
    # This is to prevent taking hundreds of pictures at once.
    PIC_CAPTURE_DELAY = 5
    # The number of seconds to wait between each consecutive call to ping().
    DEFAULT_PULSE_DELAY = 0.2

    def __init__(self, delay=DEFAULT_PULSE_DELAY):
        self.camera = PiCamera()
        self.camera.resolution = (1024, 768)
        self.delay = delay
        self.pics_taken = 0
        self.last_taken = 0
        self.is_paused = False
        self.is_released = False

        # This is where all pictures taken by the camera will be saved.
        if not os.path.isdir('captures'):
            os.mkdir('captures')

        GPIO.setmode(GPIO.BCM)

        GPIO.setup(self.TRIG, GPIO.OUT)
        GPIO.setup(self.ECHO, GPIO.IN)

    def set_delay(self, delay):
        self.delay = delay

    def pause(self):
        self.is_paused = True

    def unpause(self):
        self.is_paused = False

    def re_init(self):
        if not self.is_released:
            return

        self.camera = PiCamera()
        self.camera.resolution = (1024, 768)
        GPIO.setmode(GPIO.BCM)
        GPIO.setup(self.TRIG, GPIO.OUT)
        GPIO.setup(self.ECHO, GPIO.IN)
        self.is_released = False

    def get_config(self):
        return {
            'is_paused': self.is_paused,
            'delay': self.delay,
            'pics_taken': self.pics_taken,
            'threshold': self.THRESHOLD,
            'picture_limit': self.PICTURE_LIMIT,
            'max_ping_timeout': self.MAX_PING_TIMEOUT,
            'pic_capture_delay': self.PIC_CAPTURE_DELAY,
            'is_released': self.is_released
        }

    def ping(self):
        if self.is_paused:
            return

        GPIO.output(self.TRIG, False)
        sleep(self.delay)

        # The sensor requires a short 10 micro second pulse to trigger the module,
        # which will cause the sensor to start the ranging program (8 ultrasound bursts at 40 kHz)
        # in order to obtain an echo response. So, to create our trigger pulse, we set out
        # trigger pin high for 10 micro seconds then set it low again.
        GPIO.output(self.TRIG, True)
        sleep(0.00001)
        GPIO.output(self.TRIG, False)

        pulse_start = 0
        pulse_end = 0
        last_ping = time()

        # Sometimes the sensor might have been bumped or come loose,
        # so the time it takes to send a pulse needs to be checked.
        while GPIO.input(self.ECHO) == 0:
            pulse_start = time()
            if time() - last_ping >= self.MAX_PING_TIMEOUT:
                self._error_and_exit('Error: GPIO timeout sending pulse, exiting...')

        while GPIO.input(self.ECHO) == 1:
            pulse_end = time()
            if time() - last_ping >= self.MAX_PING_TIMEOUT:
                self._error_and_exit('Error: GPIO timeout receiving pulse, exiting...')

        distance = round((pulse_end - pulse_start) * 17150, 2)

        print('Distance: %.2f cm' % distance)

        if distance <= self.THRESHOLD:
            if time() - self.last_taken >= self.PIC_CAPTURE_DELAY:
                # Will format the date as: Apr-04-2019_11_14_00_437389PM
                file_date_format = '%b-%d-%Y_%I_%M_%S_%f%p'
                # Will format the date as: 'Apr, 04 2019 - 11:14:00 AM'
                notif_date_format = '%b, %d %Y - %I:%M:%S %p'

                url = ''
                print('Movement: %s' % str(datetime.strftime(datetime.now(), notif_date_format)))

                if self.pics_taken < self.PICTURE_LIMIT:
                    file_name = 'captures/%s.jpg' % str(datetime.strftime(datetime.now(), file_date_format))
                    self.camera.capture(file_name)
                    self.pics_taken += 1

                    url = dropbox_upload(file_name)
                    database_upload(url)

                send_push_notification({
                    'title': 'Movement Detected',
                    'body': 'Movement has been detected. Time: %s' % str(
                        datetime.strftime(datetime.now(), notif_date_format)
                    ),
                    'summary': 'Raspberry Pi',
                    'is_error': False,
                    'img_url': url
                })

                self.last_taken = time()

    def _error_and_exit(self, msg):
        send_push_notification({
            'title': 'An error has occurred',
            'body': msg,
            'summary': 'Raspberry Pi',
            'is_error': True
        })
        self.release()
        raise RuntimeError(msg)

    def release(self):
        if self.is_released:
            return

        print('Cleaning GPIO and releasing camera...')
        self.camera.close()
        GPIO.cleanup([self.TRIG, self.ECHO])
        self.is_released = True
