# ONNX Runtime's native code makes its Java results (tensors, maps, sequences) through their
# constructors, which R8 cannot see being called.
-keep class ai.onnxruntime.** { *; }

# Vosk is reached through JNA, which finds Java classes, fields and methods by name from native code.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**
