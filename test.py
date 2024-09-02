import firebase_admin
from firebase_admin import credentials, messaging

def send_fcm_message(topic, data_message):
    # Initialize the Firebase app with the service account
    cred = credentials.Certificate(r'C:\Users\Adrien\Documents\cAlgo\Sources\Plugins\Multi MACD notif\firebase_serviceAccountKey.json')
    if not firebase_admin._apps:
        firebase_admin.initialize_app(cred)

    # Create the message with high priority
    message = messaging.Message(
        data=data_message,
        topic=topic,
        android=messaging.AndroidConfig(priority='high')
    )

    # Send the message
    response = messaging.send(message)
    print('Successfully sent message:', response)

if __name__ == "__main__":
    # Replace this with your actual topic name
    TOPIC_NAME = "zephir_trading_active"

    data = {
        "title" : "Long Signal for EURUSD",
        "asset" : "EURUSD",
        "timestamp" : "2024-08-31 14:38:22",
        "side" : "LONG",
        "url" : "https://firebasestorage.googleapis.com/v0/b/zephir-trading-firebase.appspot.com/o/zephir.png?alt=media&token=340b9968-4e11-46d1-84be-a9a818f5b94a"
    }

    send_fcm_message(TOPIC_NAME, data)