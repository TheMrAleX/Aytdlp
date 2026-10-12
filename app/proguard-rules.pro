-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn org.apache.commons.**
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Optional desktop-only Jackson extensions are absent on Android.
-dontwarn java.beans.ConstructorProperties
-dontwarn java.beans.Transient
-dontwarn org.w3c.dom.bootstrap.DOMImplementationRegistry
