## Emulator Support

The SDK supports using emulators except in the following known cases:

1. Emulators with API 22 or lower have bad audio emulation, the sound is generally inaudible
2. Emulators must have Google Play services support to use FCM to receive call invites
3. Running on x86 API 25 emulators results in application crashes

In general we advise using a real device when doing development with our SDK since real-time audio is a performance oriented operation.