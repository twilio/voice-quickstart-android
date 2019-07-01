## 3.x to 4.x Migration Guide

4.0 SDK introduced a new call state `RECONNECTING`. You will need to update any logic you have implemented that relies on the call state. The simplest approach is to treat a `RECONNECTING` just like a `CONNECTED` and keep the current behavior.

4.0 has a new state `RECONNECTING` in `Call.State` and two new callbacks `onReconnecting(...)`, `onReconnected(...)` in `Call.Listener()`. Any prior implementation of `Call.Listener()` will need to be updated with the new callbacks.

```
private Call.Listener callListener() {
    return new Call.Listener() {
    
        @Override
        public void onRinging(@NonNull Call call) {
            Log.d(TAG, "Ringing");
        }
    
        @Override
        public void onConnectFailure(@NonNull Call call, @NonNull CallException error) {
            Log.d(TAG, "Connect failure");
        }
    
        @Override
        public void onConnected(@NonNull  Call call) {
            Log.d(TAG, "Connected");
        }
    
        /**
         * `onReconnecting()` callback is raised when a network change is detected and Call is already in `CONNECTED` `
         * Call.State`. If the call is in `CONNECTING` or `RINGING` when network change happened the SDK will continue 
         * attempting to connect, but a reconnect event will not be raised.
         */
        @Override
        public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
            Log.d(TAG, "Reconnecting");
        }
    
       /**
        * The call is successfully reconnected after reconnecting attempt.
        * /
        @Override
        public void onReconnected(@NonNull Call call) {
            Log.d(TAG, "Reconnected");
        }
    
        @Override
        public void onDisconnected(@NonNull Call call, @Nullable CallException error) {
           Log.d(TAG, "Disconnected");
        }
    };
}
```