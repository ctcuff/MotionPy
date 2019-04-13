import os
import dropbox
import requests

from config import *
from json import dumps
from time import time
from datetime import datetime
from pyfcm import FCMNotification
from pyfcm.errors import FCMServerError
from firebase_admin import initialize_app, db, credentials
from uuid import uuid4
from dropbox.files import WriteMode
from bs4 import BeautifulSoup


dbx = dropbox.Dropbox(DBOX_TOKEN)
cred = credentials.Certificate('firebase-admin.json')

initialize_app(cred, {'databaseURL': DATABASE_URL})

ref = db.reference('/')


def send_push_notification(payload):
    try:
        push_service = FCMNotification(api_key=API_KEY)

        start = time()
        result = push_service.notify_single_device(
            registration_id=REGISTRATION_ID, 
            data_message=payload
        )
        end = time()

        print('Notification took %.2f seconds to send' % (end - start))
        print(dumps(result, indent=3))

    except FCMServerError as e:
        # This usually occurs when the PI has a weak internet connection
        # or FCM's servers are down
        print("Couldn't send notification: %s" % e)


def database_upload(url):
    _id = str(uuid4())
    ref.update({
        _id: {
            'time': datetime.strftime(datetime.now(), "%b, %d %Y - %I:%M:%S %p"),
            'url': url,
            'id': _id,
        }
    })


def dropbox_upload(filename, overwrite=False):
    start = time()
    file = open(filename, 'rb')

    # Ensures the the file is stored in Dropbox's /motion folder
    file_path = '/motion/' + os.path.split(filename)[-1]

    dbx.files_upload(
        file.read(), 
        file_path, 
        mode=WriteMode('overwrite' if overwrite else 'add')
    )

    share_url = 'https://api.dropboxapi.com/2/sharing/create_shared_link'

    headers = {
        'Authorization': 'Bearer %s' % DBOX_OAUTH_TOKEN,
        'Content-Type': 'application/json'
    }

    result = requests.post(share_url, headers=headers, data=dumps({'path': file_path}))

    file.close()
    end = time()
    print('File took %.2f second to upload' % (end - start))
    start = time()
    # Dropbox doesn't return a downloadable link to the uploaded file so it has
    # to be parsed from the HTML directly
    page = requests.get(result.json()['url'])
    soup = BeautifulSoup(page.content, 'html.parser')
    img = soup.find('img', {'class': 'preview'})
    end = time()

    print('Took %.2f seconds to parse response' % (end - start))

    return img['src']
