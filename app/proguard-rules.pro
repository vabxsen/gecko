# kotlinx.serialization: keep generated serializers and their companions so
# reflection-free serialization keeps working after shrinking/obfuscation.
# https://github.com/Kotlin/kotlinx.serialization/blob/master/rules/common.pro
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.orca.**$$serializer { *; }
-keepclassmembers class com.orca.** {
    *** Companion;
}
-keepclasseswithmembers class com.orca.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities/DAOs are referenced directly by KSP-generated code, not reflection,
# but keep entity field names stable in case of future schema export tooling.
-keep class com.orca.core.database.entity.** { *; }

# OkHttp/okhttp-sse platform detection touches optional classes that don't exist on
# Android; R8 warnings about these are expected and harmless.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
