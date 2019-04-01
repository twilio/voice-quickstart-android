## 2.x to 3.x Migration Guide

This section describes API or behavioral changes when upgrading from Voice Android 2.x to Voice Android 3.x. Each section provides code snippets to assist in transitioning to the new API.

1. [Making a Call](#migration1)
2. [Call State](#migration2)
3. [CallInvite Changes](#migration3)
4. [Specifying a Media Region](#migration4)
5. [ConnectOptions & AcceptOptions](#migration5)
6. [Media Establishment & Connectivity](#migration6)
7. [ProGuard Configuration](#migration7)

#### <a name="migration1"></a>Making a Call

In Voice 3.x, the API to make a call has changed from `Voice.call(...)` to `Voice.connect(...)`.

```Java
Call call = Voice.connect(context, accessToken, listener);
```

#### <a name="migration2"></a>Call State

The call state `CallState` has moved to the Call class. It can be referenced as follows:

```Java
Call.State
```

#### <a name="migration3"></a>CallInvite Changes

In Voice Android 3.x, the `MessageListener` no longer raises errors if an invalid message is provided, instead a `boolean` value is returned when `boolean Voice.handleMessage(context, data, listener)` is called. The `boolean` value returns `true` when the provided data resulted in a `CallInvite` or `CancelledCallInvite` raised by the `MessageListener`. If `boolean Voice.handleMessage(context, data, listener)` returns `false` it means the data provided was not a Twilio Voice push message.

The `MessageListener` now raises callbacks for a `CallInvite` or `CancelledCallInvite` as follows:

```Java
boolean valid = handleMessage(context, data, new MessageListener() {
           @Override
           void onCallInvite(CallInvite callInvite) {
               // Show notification to answer or reject call
           }

           @Override
           void onCancelledCallInvite(CancelledCallInvite callInvite) {
               // Hide notification
           }
       });
```

The `CallInvite` has an `accept()` and `reject()` method. The `getState()` method has been removed from the `CallInvite` in favor of distinguishing between call invites and call invite cancellations with discrete stateless objects. While the `CancelledCallInvite` simply provides the `to`, `from`, and `callSid` fields also available in the `CallInvite`. The class method `getCallSid()` can be used to associate a `CallInvite` with a `CancelledCallInvite`.

In Voice Android 2.x passing a `cancel` message into `void Voice.handleMessage(...)` would not raise a callback in the following two cases:

- This callee accepted the call
- This callee rejected the call

However, in Voice Android 3.x passing a `cancel` data message into `handleMessage(...)` will always result in a callback. A callback is raised whenever a valid message is provided to `Voice.handleMessage(...)`.

Note that Twilio will send a `cancel` message to every registered device of the identity that accepts or rejects a call, even the device that accepted or rejected the call.

#### <a name="migration4"></a>Specifying a media region

Previously, a media region could be specified via `Voice.setRegion(String)`. Now this configuration can be provided as part of `ConnectOptions` or `AcceptOptions` as shown below:

```Java
ConnectOptions connectOptions = new ConnectOptions.Builder()
            .region(String)
            .build();

AcceptOptions acceptOptions = new AcceptOptions.Builder()
            .region(String)
            .build();
```

#### <a name="migration5"></a>ConnectOptions & AcceptOptions

To support configurability upon making or accepting a call new classes have been added. To create `ConnectOptions` the `ConnectOptions.Builder` must be used. Once `ConnectOptions` is created it can be provided when connecting  a `Call` as shown below:

```Java
ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
            .setParams(params)
                    .build();
Call call = Voice.connect(context, connectOptions, listener);
```

A `CallInvite` can also be accepted using `AcceptOptions` as shown below:

```Java
AcceptOptions acceptOptions = new AcceptOptions.Builder()
            .build();
CallInvite.accept(context, acceptOptions, listener);
```

#### <a name="migration6"></a>Media Establishment & Connectivity

The Voice Android 3.x SDK uses WebRTC. The exchange of real-time media requires the use of Interactive Connectivity Establishment(ICE) to establish a media connection between the client and the media server. In some network environments where network access is restricted it may be necessary to provide ICE servers to establish a media connection. We reccomend using the [Network Traversal Service (NTS)](https://www.twilio.com/stun-turn) to obtain ICE servers. ICE servers can be provided when making or accepting a call by passing them into `ConnectOptions` or `AcceptOptions` in the following way:

```Java
// Obtain the set of ICE servers from your preferred ICE server provider
Set<IceServer> iceServers = new HashSet<>();
iceServers.add(new IceServer("stun:global.stun.twilio.com:3478?transport=udp"));
iceServers.add(new IceServer("turn:global.turn.twilio.com:3478?transport=udp"));
...

IceOptions iceOptions = new IceOptions.Builder()
         .iceServers(iceServers)
         .build();

ConnectOptions.Builder connectOptionsBuilder = new ConnectOptions.Builder(accessToken)
         .iceOptions(iceOptions)
         .build();
```

#### <a name="migration7"></a>ProGuard Configuration

To enable ProGuard, follow the [official instructions](https://developer.android.com/studio/build/shrink-code#enabling-gradle) first. 

* Open your app module's ProGuard configuration (`proguard-rules.pro` in your app's module in Android Studio)
* Add the following lines at the end of your existing configuration

```
-keep class com.twilio.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class com.twilio.voice.** { *; }
-keepattributes InnerClasses

```

