import socketio
import sys
import logging
from flask import Flask, request, jsonify
from config import SERVER_KEY

app = Flask(__name__)
# Heroku might not show all errors so this ensures all
# errors are properly logged.
app.logger.addHandler(logging.StreamHandler(sys.stdout))
app.logger.setLevel(logging.ERROR)

socket = socketio.Server(async_mode='threading')
app.wsgi_app = socketio.Middleware(socket, app.wsgi_app)

valid_commands = ['start', 'stop']


@socket.on('ping')
def ping(data):
    print('I got a ping, sending a pong...')
    socket.emit('pong')


@socket.on('ying')
def ying(data):
    print('I got a ying, sending a yang...')
    socket.emit('yang')


@socket.on('Pi connected')
def connected(data):
    print('user connected')
    socket.emit('Pi connect success')


@app.route('/')
def index():
    return 'Hello, World'


@app.route('/control')
def control():
    """
    Making a request to this route with proper headers sends those headers
    to the Raspberry Pi as commands to control the sensor and camera.
    """
    print(request.headers)
    key = request.headers.get('key')

    # Ensures only authorized users can access this endpoint
    if not key == SERVER_KEY:
        return jsonify({'status': 'Error: Missing or invalid server key.'})

    command = request.headers.get('command')
    resp = 'Success'

    if command in valid_commands:
        socket.emit(command)
    else:
        resp = 'Error: %s was not a valid command.' % command
        print(resp)

    return jsonify({'status': resp})


if __name__ == '__main__':
    # Flask is single threaded by default so this allows
    # requests to be processed while also keeping the socket running.
    app.run(threaded=True)
