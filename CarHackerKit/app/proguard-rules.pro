# CarHackerKit ProGuard Rules

# Keep OBD/CAN data classes
-keep class com.carhacker.kit.obd.** { *; }
-keep class com.carhacker.kit.can.** { *; }
-keep class com.carhacker.kit.security.** { *; }

# USB Serial library
-keep class com.hoho.android.usbserial.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep data classes for serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Don't warn about missing classes
-dontwarn java.lang.invoke.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
