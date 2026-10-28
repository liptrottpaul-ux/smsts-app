# kotlinx.serialization: keep generated serializers for app models.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class uk.co.asmcoach.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class uk.co.asmcoach.**$$serializer { *; }

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# sherpa-onnx: JNI looks up these classes and fields by name.
-keep class com.k2fsa.sherpa.onnx.** { *; }
