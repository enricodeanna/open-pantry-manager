package eu.studiodeanna.openpantrymanager.ocr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import eu.studiodeanna.openpantrymanager.receipt.OcrLine
import java.nio.FloatBuffer

/**
 * A [TextReader] on ONNX Runtime, from the .onnx files of PaddleOCR's detector and recognizer and the
 * [characters] the recognizer writes, one per line. The networks take a good part of the memory while
 * open: close it when the reading is done.
 */
class OnnxTextReader(detector: ByteArray, recognizer: ByteArray, characters: String, threads: Int) : AutoCloseable {
    private val detect = OnnxNetwork(detector, threads)
    private val recognize = OnnxNetwork(recognizer, threads)
    private val reader = TextReader(detect, recognize, characters.split('\n'))

    fun read(picture: Picture): List<OcrLine> = reader.read(picture)

    override fun close() {
        detect.close()
        recognize.close()
    }
}

/** A network run by ONNX Runtime on the processor. */
private class OnnxNetwork(model: ByteArray, threads: Int) : Network, AutoCloseable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session = environment.createSession(
        model,
        OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(threads)
            // ALL_OPT also rearranges the networks' data for ARM's kernels: no faster on a phone, and
            // with half-precision networks it could not make the session at all.
            setOptimizationLevel(OrtSession.SessionOptions.OptLevel.EXTENDED_OPT)
        },
    )
    private val input = session.inputNames.first()

    override fun run(input: Tensor): Tensor {
        val shape = LongArray(input.shape.size) { input.shape[it].toLong() }
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(input.data), shape).use { tensor ->
            session.run(mapOf(this.input to tensor)).use { result ->
                val output = result.get(0) as OnnxTensor
                val buffer = output.floatBuffer
                return Tensor(IntArray(output.info.shape.size) { output.info.shape[it].toInt() }, FloatArray(buffer.remaining()).also(buffer::get))
            }
        }
    }

    override fun close() = session.close()
}
