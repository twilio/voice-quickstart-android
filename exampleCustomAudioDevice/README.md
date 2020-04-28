# Twilio Voice AudioDevice Example

The project demonstrates how to use Twilio's Programmable Voice SDK with audio playback and recording functionality provided by a custom `AudioDevice`.

The example demonstrates the custom audio device **exampleCustomAudioDevice**, which uses android audio subsystem to playback and record audio at 44.1KHz with built-in echo and noise cancellation. 

1. The upstream audio subsystem receives remote participant's playout audio samples from the code audio device module and plays them in the speaker.
2. The downstream audio subsystem is capable to switch audio source between the local participant's microphone audio and audio from a file. The Voice SDK receives and delivers the recorded audio samples to the core audio device module.

This diagram describes how **exampleCustomAudioDevice** uses `AudioDevice` to receive and deliver audio samples from/to the core audio device.

<img width="800px" src="../images/quickstart/audio-device-example.png"/>

### Setup

Refer to the [README](https://github.com/twilio/voice-quickstart-android/blob/master/README.md) for instructions on how to generate an access token and make an outbound `Call`.

### Running

Once you have configured your access token, build and run the example. Press the call button to open the call dialog and make an outbound call to a [client](https://github.com/twilio/voice-quickstart-android#bullet10) or to a [PSTN](https://github.com/twilio/voice-quickstart-android#11-make-client-to-pstn-call) number.

<kbd><img width="400px" src="../images/quickstart/make_call_custom_audio_device.png"/></kbd>

Audio from a file is selected by default. Once the Call is connected, music starts to play.

<kbd><img width="400px" src="../images/quickstart/audio_device_music_file_plays.png"/></kbd>

You can switch to microphone by clicking the `♫` button.

<kbd><img width="400px" src="../images/quickstart/audio_device_microphone.png"/></kbd>

Note: The switch between audio file and microphone always starts the music from the begining of the file.

## Troubleshooting Audio
The following sections provide guidance on how to ensure optimal audio quality in your applications using default audio device.

### Configuring AudioManager

Follow the [README](https://github.com/twilio/voice-quickstart-android#configuring-audiomanager) to configure audio manager.

### Configuring Hardware Audio Effects Using Custom Audio Device
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

### Configuring OpenSL ES
Follow the [README](https://github.com/twilio/voice-quickstart-android#configuring-opensl-es) to configure OpenSL ES.

### Managing Device Specific Configurations
Follow the [README](https://github.com/twilio/voice-quickstart-android#managing-device-specific-configurations) to manage device specific configurations.

### Handling Low Headset Volume
Follow the [README](https://github.com/twilio/voice-quickstart-android#handling-low-headset-volume) to handle low handset volume.
