# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.gothwad.computer.**$$serializer { *; }
-keepclassmembers class com.gothwad.computer.** {
    *** Companion;
}
-keepclasseswithmembers class com.gothwad.computer.** {
    kotlinx.serialization.KSerializer serializer(...);
}
