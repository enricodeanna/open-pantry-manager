# ONNX Runtime's native code makes its Java results (tensors, maps, sequences) through their
# constructors, which R8 cannot see being called.
-keep class ai.onnxruntime.** { *; }
