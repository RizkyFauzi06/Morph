# Morph release (R8) rules. Libraries add their own on top: FFmpegKit keeps its JNI callbacks,
# kotlinx-coroutines keeps its ServiceLoader entries. These cover what R8 cannot see from code.

# --- libmorph-native (x264): C++ exports Java_com_morph_app_MorphNative_<method>, so the class
# name and the native method names must not change. C++ never calls back into Java.
-keep class com.morph.app.MorphNative {
    native <methods>;
}

# --- FFmpegKit: its native code calls FFmpegKitConfig by name (log, statistics, SAF open/close)
# and fills session, statistics and media-information objects. Keep the whole API instead of
# chasing every JNI lookup in a prebuilt library.
-keep class com.arthenica.ffmpegkit.** { *; }
# smart-exception formats FFmpegKit's error messages
-keep class com.arthenica.smartexception.** { *; }

# --- Kotlin metadata and coroutines
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep class kotlin.Metadata { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
# kotlinx.serialization is not a dependency and no class is @Serializable, so it needs no rules.

# --- Readable release stack traces (decode with build/outputs/mapping/release/mapping.txt)
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
