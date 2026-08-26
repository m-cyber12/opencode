# Keep kotlinx.serialization models used via reflection-free generated serializers
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class dev.opencode.android.** {
    *** Companion;
}
-keepclasseswithmembers class dev.opencode.android.** {
    kotlinx.serialization.KSerializer serializer(...);
}
