
# SQLCipher calls into Java classes from native code: keep them as they are.
-keep class net.zetetic.database.** { *; }
-keep interface net.zetetic.database.** { *; }
# Room entities are read by generated code.
-keep class com.bluemob.app.data.** { *; }
# Drop debug logging from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# The offline-AI engine (MediaPipe) is called from native code and reads protobuf messages by reflection.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**
-dontwarn com.google.protobuf.**
-dontwarn javax.lang.model.**
-dontwarn com.google.auto.value.**
