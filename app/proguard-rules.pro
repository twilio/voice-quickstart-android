# Twilio Programmable Voice
-keep class com.twilio.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-keep class com.twilio.voice.** { *; }
-keepattributes InnerClasses

# Retrofit
-dontwarn okio.**
-dontwarn retrofit.**
-keep class retrofit.** { *; }
-keepclassmembers,allowobfuscation interface * {
    @retrofit.http.** <methods>;
}

-dontwarn com.squareup.okhttp.**
