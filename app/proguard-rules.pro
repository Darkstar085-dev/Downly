# Downly-specific R8 rules. Keep narrow and add classes only when required.
-keepattributes Signature,*Annotation*

# Suppress missing optional dependency warnings from Apache Commons Compress
-dontwarn com.github.luben.zstd.**
-dontwarn org.tukaani.xz.**

