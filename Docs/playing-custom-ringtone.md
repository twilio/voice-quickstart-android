## Playing Custom Ringtone 

When [answerOnBridge](https://www.twilio.com/docs/voice/twiml/dial#answeronbridge) is enabled in the `<Dial>` TwiML verb, the caller will not hear the ringback while the call is ringing and awaiting to be accepted on the callee's side. The application can use the `SoundPoolManager` to play custom audio files between the `Call.Listener.onRinging()` and the `Call.Listener.onConnected()` callbacks. To enable this behavior, add `playCustomRingback` as an environment variable or a property in `local.properties` file and set it to `true`.

```
playCustomRingback=true
```