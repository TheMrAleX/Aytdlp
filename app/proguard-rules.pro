-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn org.apache.commons.**
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Optional desktop-only Jackson extensions are absent on Android.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry

# Commons Compress registers ZIP extra fields using reflective no-arg construction.
# R8 must retain concrete implementations and their constructors for runtime extraction.
-keep,allowobfuscation class * implements org.apache.commons.compress.archivers.zip.ZipExtraField {
    public <init>();
}
