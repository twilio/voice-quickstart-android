## 3.0 New Features

Voice Android 3.0 has a number of new features listed below:

1. [WebRTC](#feature1)
2. [Custom Parameters](#feature2)
3. [Hold](#feature3)
4. [Ringing](#feature4)
5. [Stats](#feature5)
6. [Preferred Audio Codec](#feature6)

#### <a name="feature1"></a>WebRTC

The SDK is built using Chromium WebRTC for Android. This ensures that over time developers will get the best real-time media streaming capabilities available for Android. Additionally, upgrades to new versions of Chromium WebRTC will happen without changing the public API whenever possible.

#### <a name="feature2"></a>Custom Parameters

You can now send parameters from the caller to the callee when you make a call. The key/value data is sent from the Voice SDK to your TwiML Server Application, and passed into TwiML to reach the callee.

##### Sending parameters to your TwiML Server Application for outgoing calls

Parameters can be sent to your TwiML Server Application by specifying them in the `ConnectOptions` builder as follows:

```Java
final Map<String, String> params = new HashMap<>();
params.put("caller_first_name", "alice");
params.put("caller_last_name", "smith");

ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
    .params(params)
    .build();

call = Voice.connect(context, connectOptions, listener);
```

These will arrive as either POST parameters or URL query parameters, depending on which HTTP method you configured for your TwiML Server Application in the [console](https://www.twilio.com/console/voice/twiml/apps).

Once available on your TwiML Server Application you can use them to populate your TwiML response as described in the next section.

##### Getting parameters from your TwiML Server Application for incoming calls

Parameters can be sent to a callee by initiating a TwiML [\<Dial\>](https://www.twilio.com/docs/voice/twiml/dial). Use the `<Parameter>` attribute to specify your key/value parameters as shown below:

```Java
// Pass custom parameters in TwiML
<?xml version="1.0" encoding="UTF-8"?>
	<Response>
		<Dial answerOnBridge="false" callerId="client:alice">
			<Client>
				<Identity>bob</Identity>
				<Parameter name="caller_first_name" value="alice"  />
				<Parameter name="caller_last_name" value="smith"  />
			</Client>
		</Dial>
	</Response>
```

When the call invite push message arrives to the callee it will have the specified parameters. The key/value parameters can then be retrieved as a Map from the `CallInvite.getCustomParameters()` method.

#### <a name="feature3"></a>Hold

Previously, there was no way to hold a call. Hold can now be called on the `Call` object as follows:

```Java
call.hold(boolean);
```

#### <a name="feature4"></a>Ringing

Ringing is now provided as a call state. A callback corresponding to this state transition is emitted once before the `Call.Listener.onConnected(...)` callback when the callee is being alerted of a Call. The behavior of this callback is determined by the `answerOnBridge` flag provided in the `Dial` verb of your TwiML application associated with this client. If the `answerOnBridge` flag is `false`, which is the default, the `Call.Listener.onConnected(...)` callback will be emitted immediately after `Call.Listener.onRinging(...)`. If the `answerOnBridge` flag is `true`, this will cause the call to emit the onConnected callback only after the call is answered. See [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge) for more details on how to use it with the Dial TwiML verb. If the TwiML response contains a Say verb, then the call will emit the `Call.Listener.onConnected(...)` callback immediately after `Call.Listener.onRinging(...)` is raised, irrespective of the value of `answerOnBridge` being set to `true` or `false`.

These changes are added as follows:

```Java
public class Call {

	public enum State {
		CONNECTING,
		RINGING, // State addition
		CONNECTED,
		DISCONNECTED
	}

	public interface Listener {
	   void onConnectFailure(@NonNull Call call, @NonNull CallException callException);
		void onRinging(@NonNull Call call); // Callback addition
		void onConnected(@NonNull Call call);
		void onDisconnected(@NonNull Call call, @Nullable CallException callException);
	}
}
```

#### <a name="feature5"></a>Stats

Statistics related to the media in the call can now be retrieved by calling `Call.getStats(StatsListener listener)`. The `StatsListener` returns a `StatsReport` that provides statistics about the local and remote audio in the call.

#### <a name="feature6"></a>Preferred Audio Codec

You can provide your preferred audio codecs in the `ConnectOptions` and the `AcceptOptions`. Opus is the default codec used by the mobile infrastructure. To use PCMU as the negotiated audio codec instead of Opus you can add it as the first codec in the preferAudioCodecs list.

```Java
 ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                        .params(params)
                        .preferAudioCodecs(Arrays.asList(new PcmuCodec(), new OpusCodec()))
                        .build();
Call call = Voice.connect(VoiceActivity.this, connectOptions, callListener);
```
