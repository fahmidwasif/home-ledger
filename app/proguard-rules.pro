# LiteRT-LM calls back into Kotlin/Java from native code and serialises with Gson.
-keep class com.google.ai.edge.litertlm.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn com.google.ai.edge.litertlm.**
# kotlinx.serialization (backup format, AI JSON parsing)
-keepattributes *Annotation*, InnerClasses, Signature
-keepclassmembers @kotlinx.serialization.Serializable class nz.afhome.ledger.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class nz.afhome.ledger.**$$serializer { *; }
-keep class nz.afhome.ledger.data.** { *; }
# ML Kit discovers its components by reflection (no-arg constructors of ComponentRegistrar classes).
-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_bundled_common.** { *; }
