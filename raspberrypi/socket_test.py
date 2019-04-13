"""
A small test to ensure the Raspberry Pi can actually connect
to the web socket without timing out
"""

import socketio
from time import time
from threading import Thread
from config import SERVER_URL

timing = {
    'start': 0,
    'end': 0
}
DEFAULT_TIMEOUT = 30

socket = socketio.Client()


def init_socket():
    socket.connect(SERVER_URL)
    socket.emit('Pi connected')


@socket.on('Pi connect success')
def connect_success(data):
    timing['end'] = time()
    print(
        'Success: got response in %.2f seconds.'
        % (timing['end'] - timing['start'])
    )


if __name__ == '__main__':
    thread = Thread(target=init_socket)
    thread.start()
    print('Testing connection...')

    timing['start'] = time()
    # End the socket connection after the default timeout if there
    # is no response from the server
    timeout = timing['start'] + DEFAULT_TIMEOUT

    # Make the program wait until we get a response
    while timing['end'] - timing['start'] <= 0:
        try:
            if timeout - time() <= 0:
                print('Connection to socket timed out, disconnecting...')
                socket.disconnect()
                exit()
        except KeyboardInterrupt:
            print('Disconnecting...')
            break

    socket.disconnect()
