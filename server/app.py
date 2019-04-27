import socketio
import sys
import logging
from flask import Flask, request, jsonify
from config import SERVER_KEY
from socketio.exceptions import TimeoutError

app = Flask(__name__)
# Heroku might not show all errors so this ensures all
# errors are properly logged.
app.logger.addHandler(logging.StreamHandler(sys.stdout))
app.logger.setLevel(logging.ERROR)

socket = socketio.Server(async_mode='threading')
app.wsgi_app = socketio.Middleware(socket, app.wsgi_app)

DEFAULT_TIMEOUT = 10

valid_commands = ['start', 'pause', 'stop', 'power_off']


@app.route('/')
def index():
    return 'Hello, World'


@socket.on('ping')
def ping(data):
    print('Got a ping, sending a pong...')
    socket.emit('pong')


@socket.on('ying')
def ying(data):
    print('Got a ying, sending a yang...')
    socket.emit('yang')


@socket.on('Pi connected')
def connected(data):
    print('user connected')
    socket.emit('Pi connect success')


@socket.on('Pi command success')
def command_success(data):
    print('Raspberry Pi received command')


@app.route('/control')
def control():
    """
    Making a request to this route with proper headers sends those headers
    to the Raspberry Pi as commands to control the sensor and camera.
    """
    key = request.headers.get('key')
    response = {
        'status': 200,
        'error_msg': ''
    }

    # Ensures only authorized users can access this endpoint
    if key != SERVER_KEY:
        response['error_msg'] = 'Error: Missing or invalid server key.'
        response['status'] = 400
        return jsonify(response)

    command = request.headers.get('command')

    if command:
        command = command.lower()

    if command in valid_commands:
        # Wait until the Raspberry Pi receives the command before
        # sending a response back to the client
        try:
            socket.call(event=command, timeout=DEFAULT_TIMEOUT)
        except TimeoutError:
            response['error_msg'] = 'Timed out while sending response. The Raspberry Pi might be offline.'
            response['status'] = 504

    else:
        response['error_msg'] = '%s was not a valid command. Valid commands are %s.' % (command, valid_commands)
        response['status'] = 400

    return jsonify(response)


if __name__ == '__main__':
    # Flask is single threaded by default so this allows
    # requests to be processed while also keeping the socket running
    app.run(threaded=True)
