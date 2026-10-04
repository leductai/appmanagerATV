# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.kaos.tvappmanager.** {
    kotlinx.serialization.KSerializer serializer(...);
}
