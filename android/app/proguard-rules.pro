# MediaPipe loads native code and reflects on task options.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**

# Room database: keep all entity, DAO, and database classes for reflection.
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.clashfit.data.** { *; }
-keepattributes RuntimeVisibleAnnotations

# kotlinx.serialization: keep serializers for our models and config records.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.clashfit.**$$serializer { *; }
-keepclassmembers class com.clashfit.** { *** Companion; }
-keepclasseswithmembers class com.clashfit.** { kotlinx.serialization.KSerializer serializer(...); }

# Filament: the renderer is native, and its Java classes are reached from JNI by name.
-keep class com.google.android.filament.** { *; }
-dontwarn com.google.android.filament.**
