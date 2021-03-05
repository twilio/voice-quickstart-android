## Managing Push Credentials

A Push Credential is a record for a push notification channel, for Android this Push Credential is a push notification channel record for FCM or GCM. Push Credentials are managed in the console under [Mobile Push Credentials](https://www.twilio.com/console/voice/sdks/credentials).

Whenever a registration is performed via `Voice.register(…)` in the Android SDK the `identity` and the `Push Credential SID` provided in the JWT based access token, along with the `FCM/GCM token` are used as a unique address to send push notifications to this application instance whenever a call is made to reach that `identity`. Using `Voice.unregister(…)` removes the association for that `identity`.

### Updating a Push Credential

If you need to change or update your server key token provided by Firebase (under `Project Settings` → `Cloud Messaging` → `Server key`) you can do so by selecting the Push Credential in the [console](https://www.twilio.com/console/voice/sdks/credentials) and adding your new `Server key` in the text box provided on the Push Credential page shown below:

<img height="500px" src="https://raw.githubusercontent.com/twilio/voice-quickstart-android/master/images/quickstart/updating_push_credential.png">

### Deleting a Push Credential

We **do not recommend that you delete a Push Credential** unless the application that it was created for is no longer being used.

When a Push Credential is deleted **any associated registrations made with this Push Credential will be deleted**. Future attempts to reach an `identity` that was registered using the Push Credential SID of this deleted push credential will fail.

If you are certain you want to delete a Push Credential you can click on `Delete this Credential` on the [console](https://www.twilio.com/console/voice/sdks/credentials) page of the selected Push Credential.

Please ensure that after deleting the Push Credential you remove or replace the Push Credential SID when generating new access tokens.