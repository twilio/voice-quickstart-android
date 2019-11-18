## 4.x to 5.x Migration Guide

This document provides migration steps to 5.x release. `5.0.0` introduces an update to the Programmable Voice call model. Prior to `5.0.0`, when `Voice.register(...)` was invoked, the Voice SDK registered for two push notifications: a call and cancel push notification. The SDK now handles incoming call cancellations via a dedicated signaling mechanism. The `cancel` push notification is no longer required or supported by new releases of the SDK.

If your application supports incoming calls, you MUST perform the following steps to comply with the new call model in 5.x:

1. Upgrade Twilio Voice Android SDK to 5.0.0
2. You must register via `Voice.register` when your App starts. This ensures that your App no longer receives “cancel” push notifications. A valid call push notification, when passed to `Voice.handleMessage(...)`, will still result in a `CallInvite` being raise to the provided `MessageListener`. A `CancelledCallInvite` will be raised to the provided `MessageListener` if any of the following events occur:
    - The call is prematurely disconnected by the caller.
    - The callee does not accept or reject the call within 30 seconds.
    - The Voice SDK is unable to establish a connection to Twilio.


    A `CancelledCallInvite` will not be raised if a `CallInvite` is accepted or rejected.


    To register with the new SDK when the app is launched:
    
    ```
    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(String accessToken, String fcmToken) {
                Log.d(TAG, "Successfully registered FCM " + fcmToken);
            }
    
            @Override
            public void onError(RegistrationException error, String accessToken, String fcmToken) {
                String message = String.format("Registration Error: %d, %s", error.getErrorCode(), error.getMessage());
                Log.e(TAG, message);
            }
        };
    }
    
    private void registerForCallInvites() {
        final String fcmToken = FirebaseInstanceId.getInstance().getToken();
        if (fcmToken != null) {
            Log.i(TAG, "Registering with FCM");
            Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
        }
    }

    ```

    Please note that if the app is updated to use 5.0.0 release but never launched to perform the registration, the mobile client will still receive "cancel" notifications. If “cancel” notification is passed to `Voice.handleMessage(…)`, it will return `false`. 
3. Both `Voice.handleMessage(...)` methods require an Android `Context` as the first argument. You must update the method call to match the new method signature.
4. `MessageListener.onCancelledCallInvite` has been updated to include `@Nullable` `CallException callException` as the second argument. A `CallException` will be provided if a network or server error resulted in the cancellation. You need to update `MessageListener` implementation in your code to the following:

```
    boolean valid = Voice.handleMessage(context, remoteMessage.getData(), new MessageListener() {
        @Override
        public void onCallInvite(@NonNull CallInvite callInvite) {
            // Handle CallInvite
        }
    
        @Override
       public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite, @Nullable CallException callException) {
            // Handle CancelledCallInvite
        }
    });
```

5. If you were previously toggling `enableInsights` or specifying a `region` via `ConnectOptions`, you must now set the `insights` and `region` property via `Voice.enableInsights(…)` and `Voice.setRegion(…)` respectively. You must do so before calling `Voice.connect(…)` or `Voice.handleMessage(…)`. 
    Please note : 
    - Sending stats data to Insights is enabled by default
    - The default region uses Global Low Latency routing, which establishes a connection with the closest region to the user

```
    Voice.enableInsights(true);
    Voice.setRegion(region);
    ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
            .build();
    call = Voice.connect(getApplicationContext(), connectOptions, outgoingCallListener());
```

You can reference the 5.0.0 quickstart when migrating your application.
A summary of the API changes and new Insights events can be found in the 5.0.0 changelog.
