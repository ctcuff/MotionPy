"""
Small test to find an initial distance as well as verify
the sensor actually works.
"""
import RPi.GPIO as GPIO
from time import sleep, time


TRIG = 4
ECHO = 18
pulse_start = 0
pulse_end = 0
pulse_duration = 0


GPIO.setmode(GPIO.BCM)

GPIO.setup(TRIG, GPIO.OUT)
GPIO.setup(ECHO, GPIO.IN)
GPIO.output(TRIG, False)

print('Checking distance...')

# Give the sensor time to settle
sleep(2)

GPIO.output(TRIG, True)
sleep(0.00001)
GPIO.output(TRIG, False)

while GPIO.input(ECHO) == 0:
	pulse_start = time()

while GPIO.input(ECHO) == 1:
      pulse_end = time()      
      pulse_duration = pulse_end - pulse_start

distance = round(pulse_duration * 17150, 2)

print('Read distance of %.2f cm' % distance)

GPIO.cleanup()