# Only for `-Pshrink` debug builds (a smaller APK to send for testing): unused library code is removed, but nothing
# of BlueMob's own is renamed or dropped, and the offline-AI engine (called from native code) is kept whole.
-dontobfuscate
-keep class com.bluemob.app.** { *; }
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
