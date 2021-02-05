## Troubleshooting Audio
The following sections provide guidance on how to ensure optimal audio quality in your applications.

### Managing Audio Devices with AudioSwitch
The quickstart uses [AudioSwitch](https://github.com/twilio/audioswitch) to control [audio focus](https://developer.android.com/guide/topics/media-apps/audio-focus) and manage audio devices within the application. If you have an issue or question related to audio management, please open an issue in the [AudioSwitch](https://github.com/twilio/audioswitch) project.

### Configuring Hardware Audio Effects

#### Voice Android SDK Version 5.2.x+
Our library performs acoustic echo cancellation (AEC) and noise suppression (NS) using device hardware by default. Using device hardware is more efficient, but some devices do not implement these audio effects well. If you are experiencing echo or background noise on certain devices reference the following snippet for enabling software implementations of AEC and NS.

    /*
     * Execute any time before invoking `Voice.connect(...)` or `CallInvite.accept(...)`.
     */

    // Use software AEC
    DefaultAudioDevice defaultAudioDevice = new DefaultAudioDevice();
    defaultAudioDevice.setUseHardwareAcousticEchoCanceler(false);
    Voice.setAudioDevice(defaultAudioDevice);

    // Use sofware NS
    DefaultAudioDevice defaultAudioDevice = new DefaultAudioDevice();
    defaultAudioDevice.setUseHardwareNoiseSuppressor(false);
    Voice.setAudioDevice(defaultAudioDevice);
    
    
#### Voice Android SDK Version below 5.1.x
Our library performs acoustic echo cancellation (AEC), noise suppression (NS), and auto gain
control (AGC) using device hardware by default. Using device hardware is more efficient, but some
devices do not implement these audio effects well. If you are experiencing echo, background noise,
or unexpected volume levels on certain devices reference the following snippet for enabling
software implementations of AEC, NS, and AGC.

    /*
     * Execute any time before invoking `Voice.connect(...)` or `CallInvite.accept(...)`.
     */
    // Use software AEC
    tvo.webrtc.voiceengine.WebRtcAudioUtils.setWebRtcBasedAcousticEchoCanceler(true);

    // Use sofware NS
    tvo.webrtc.voiceengine.WebRtcAudioUtils.setWebRtcBasedNoiseSuppressor(true);

    // Use software AGC
    tvo.webrtc.voiceengine.WebRtcAudioUtils.setWebRtcBasedAutomaticGainControl(true);

### Configuring OpenSL ES
Starting with Voice SDK 4.3.0, our library does not use [OpenSL ES](https://developer.android.com/ndk/guides/audio/opensl/index.html)
for audio playback by default. Prior versions starting with Voice SDK 3.0.0 did use OpenSL ES by default. Using OpenSL ES is more efficient, but can cause
problems with other audio effects. For example, we found on the Nexus 6P that OpenSL ES affected
the device's hardware echo canceller so we blacklisted the Nexus 6P from using OpenSL ES. If you
are experiencing audio problems with a device that cannot be resolved using software audio effects,
reference the following snippet for enabling OpenSL ES:

    /*
     * Execute any time before invoking `Voice.connect(...)` or `CallInvite.accept(...)`.
     */

    // Enable OpenSL ES
    tvo.webrtc.voiceengine.WebRtcAudioManager.setBlacklistDeviceForOpenSLESUsage(false);

    // Check if OpenSL ES is disabled
    tvo.webrtc.voiceengine.WebRtcAudioUtils.deviceIsBlacklistedForOpenSLESUsage();

### Managing Device Specific Configurations
The Voice Android SDK does not maintain a list of devices for which hardware effects or OpenSL ES are disabled. We recommend maintaining a list in your own application and disabling these effects as needed. The [Signal App provides a great example](https://github.com/signalapp/Signal-Android/blob/master/src/org/thoughtcrime/securesms/ApplicationContext.java#L250) of how to maintain a list and disable the effects as needed.

### Handling Low Headset Volume
If your application experiences low playback volume, we recommend the following snippets: 

#### Android N and Below
```
int focusRequestResult = audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
    
                                   @Override
                                   public void onAudioFocusChange(int focusChange) {
                                   }
                               }, AudioManager.STREAM_VOICE_CALL,
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
```
                     

#### Android O and Up :
```
AudioAttributes playbackAttributes = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build();
AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(playbackAttributes)
        .setAcceptsDelayedFocusGain(true)
        .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
            @Override
            public void onAudioFocusChange(int i) {
            }
        })
        .build();
```    