# MediaPipe loads native code and reflects on task options.
-keep class com.google.mediapipe.** { *; }
-keep class com.google.protobuf.** { *; }
-dontwarn com.google.mediapipe.**

# kotlinx.serialization: keep serializers for our models and config records.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.clashfit.**$$serializer { *; }
-keepclassmembers class com.clashfit.** { *** Companion; }
-keepclasseswithmembers class com.clashfit.** { kotlinx.serialization.KSerializer serializer(...); }
