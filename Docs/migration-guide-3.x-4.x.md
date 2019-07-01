## 3.x to 4.x Migration Guide

This section describes API or behavioral changes when upgrading from Voice Android 3.x to Voice Android 4.x.
4.x has a new state `RECONNECTING` in `Call.State` and two new callbacks `onReconnecting(...)`, `onReconnected(...)` in `Call.Listener()`. Any prior implementation of `Call.Listener()` will need to be updated with the new callbacks.

```
private Call.Listener callListener() {
    return new Call.Listener() {
    
        @Override
        public void onRinging(Call call) {
            Log.d(TAG, "Ringing");
        }
    
        @Override
        public void onConnectFailure(Call call, CallException error) {
            Log.d(TAG, "Connect failure");
        }
    
        @Override
        public void onConnected(Call call) {
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
        public void onDisconnected(Call call, CallException error) {
           Log.d(TAG, "Disconnected");
        }
    };
}
```