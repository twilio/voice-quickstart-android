## 4.0 New Features

Voice Android 4.0 has the following new features listed below:

1. [Reconnecting State and Callbacks](#feature1)


#### <a name="feature1"></a>Reconnecting State and Callbacks

`RECONNECTING` is now provided as a call state. A callback `onReconnecting(...)` corresponding to this state transition is emitted after the `Call.Listener.onConnected(...)` callback when a network change is detected and Call is already in `CONNECTED` state. If the call is in `CONNECTING`or in `RINGING` state when network change happened, the SDK will continue attempting to connect and will not raise a callback. If a `Call` is reconnected after reconnectiong attempts, `onReconnected(...)` callback is raised and call state transitions to `CONNECTED`.

These changes are added as follows:

```Java
public class Call {

	public enum State {
		CONNECTING,
		RINGING, 
		CONNECTED,
		RECONNECTING, // State addition
		DISCONNECTED
	}

	public interface Listener {
		void onConnectFailure(@NonNull Call call, @NonNull CallException callException);
		void onRinging(@NonNull Call call); 
		void onConnected(@NonNull Call call);
		void onReconnecting(@NonNull Call call, @NonNull CallException callException); // Callback addition
		void onReconnected(@NonNull Call call); // Callback addition
		void onDisconnected(@NonNull Call call, @Nullable CallException callException);
	}
}
```