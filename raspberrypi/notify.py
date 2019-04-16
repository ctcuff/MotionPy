import os
import requests
import pyfcm
import firebase_admin
import pyrebase
from config import *
from json import dumps
from time import time
from datetime import datetime
from uuid import uuid4
from bs4 import BeautifulSoup

# Allows read/writes to Firebase Storage (this is where the images are saved)
# and Firebase Database
firebase = pyrebase.initialize_app({
    'apiKey': API_KEY,
    'authDomain': '%s.firebaseapp.com' % PROJECT_ID,
    'databaseURL': DATABASE_URL,
    'storageBucket': '%s.appspot.com' % PROJECT_ID,
    'serviceAccount': 'firebase-admin.json'
})
storage = firebase.storage()
database = firebase.database()

def send_push_notification(payload):
    try:
        push_service = pyfcm.FCMNotification(api_key=API_KEY)

        start = time()
        result = push_service.notify_single_device(
            registration_id=REGISTRATION_ID, 
            data_message=payload
        )
        end = time()

        print('Notification took %.2f seconds to send' % (end - start))
        print(dumps(result, indent=3))

    except pyfcm.errors.FCMServerError as e:
        # This usually occurs when the PI has a weak internet connection
        # or FCM's servers can't be reached for some reason
        print("Couldn't send notification: %s" % e)


def database_upload(image_url):
    _id = str(uuid4())
    database.update({
        _id: {
            'time': datetime.strftime(datetime.now(), '%b, %d %Y - %I:%M:%S %p'),
            'url': image_url,
            'id': _id,
        }
    })


def dropbox_upload(filename):
    start = time()
    storage.child('captures/%s' % os.path.basename(filename)).put(filename)
    end = time()
    print('Took %.2f seconds to upload' % (end - start))
    return storage.child(filename).get_url(None)
