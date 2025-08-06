# Add these rules to your app/proguard-rules.pro file

# Keep Click SDK classes
-keep class uz.click.mobilesdk.** { *; }
-dontwarn uz.click.mobilesdk.**

# Keep Moshi and Kotlin reflection
-keep class com.squareup.moshi.** { *; }
-keep @com.squareup.moshi.JsonQualifier interface *
-keep class **JsonAdapter { *; }
-keep class * extends com.squareup.moshi.JsonAdapter { *; }

# Keep Kotlin reflection for Moshi
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }

# Keep all Kotlin data classes (they might be serialized by Click SDK)
-keep @kotlin.Metadata class * {
    <fields>;
    <methods>;
}

# Keep Kotlin serialization annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Click SDK's InitialRequest class specifically
-keep class uz.click.mobilesdk.core.data.InitialRequest { *; }
-keep class uz.click.mobilesdk.core.data.** { *; }

# Keep all classes with @JsonClass annotation (Moshi generated)
-keep @com.squareup.moshi.JsonClass class * { *; }

# Don't obfuscate parameter names (Kotlin reflection needs them)
-keepparameternames