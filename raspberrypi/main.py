import socketio
import schedule
from threading import Thread
from sensor import Sensor
from config import SERVER_URL
from time import sleep
from subprocess import call

socket = socketio.Client()
sensor = Sensor()
status = {'stopped': False}


def init():
    print('Connecting...')
    socket.connect(SERVER_URL)
    socket.emit('Pi connected')


@socket.on('Pi connect success')
def connect_success(data):
    print('Successfully connected, listening for commands...')


@socket.on('start')
def start(data):
    # Pi command success is emitted so that the
    # server knows that the Pi received the command
    socket.emit('Pi command success')
    sensor.unpause()


@socket.on('pause')
def pause(data):
    socket.emit('Pi command success')
    sensor.pause()


@socket.on('stop')
def stop(data):
    socket.emit('Pi command success')
    status['stopped'] = True


@socket.on('power_off')
def power_off(data):
    socket.emit('Pi command success')

    # Pause the sensor before it's released to ensure that
    # it isn't released in the middle of a ping
    sensor.pause()

    # Give the GPIO pins a second to fully settle
    sleep(1)
    sensor.release()
    socket.disconnect()
    call("sudo shutdown -h now", shell=True)


if __name__ == '__main__':
    # Listen for socket responses in the background while
    # the sensor listens for movement
    thread = Thread(target=init)

    # Ping the server every 10 minutes to make sure it
    # doesn't sleep due to inactivity
    schedule.every(10).minutes.do(lambda: socket.emit('ping'))

    thread.start()
    sensor.pause()

    while not status['stopped']:
        try:
            sensor.ping()
            schedule.run_pending()
        except KeyboardInterrupt:
            sensor.release()
            socket.disconnect()
            break

    sensor.pause()
    sleep(1)
    sensor.release()
    socket.disconnect()
