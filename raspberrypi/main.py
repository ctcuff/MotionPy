import socketio
from threading import Thread
from sensor import Sensor
from config import SERVER_URL

socket = socketio.Client()
sensor = Sensor()


def init():
    socket.connect(SERVER_URL)
    socket.emit('Pi connected')


@socket.on('Pi connect success')
def connect_success(data):
    print('Successfully connected, listening for commands...')


@socket.on('start')
def start(data):
    sensor.unpause()


@socket.on('stop')
def stop(data):
    sensor.pause()


if __name__ == '__main__':
    # Listen for socket responses in the background while
    # the sensor listens for movement
    thread = Thread(target=init)
    thread.start()
    sensor.pause()
    while True:
        try:
            sensor.ping()
        except KeyboardInterrupt:
            sensor.release()
            socket.disconnect()
            break
