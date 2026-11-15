package ai.drivetalk.audio

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Real PCM silence keeps inter-utterance pauses inside Media3's focus/pause lifecycle. */
fun writeSilenceWav(file: File, seconds: Int): File {
    require(seconds in 1..10)
    val sampleRate=16_000
    val bytes=sampleRate * seconds * 2
    val header=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        .put("RIFF".toByteArray()).putInt(36+bytes).put("WAVEfmt ".toByteArray())
        .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate*2)
        .putShort(2).putShort(16).put("data".toByteArray()).putInt(bytes).array()
    file.outputStream().use { out -> out.write(header); out.write(ByteArray(bytes)) }
    return file
}
