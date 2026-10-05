
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
