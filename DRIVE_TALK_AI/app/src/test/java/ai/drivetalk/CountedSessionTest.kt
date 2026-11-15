package ai.drivetalk
import ai.drivetalk.learning.*
import ai.drivetalk.audio.writeSilenceWav
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class CountedSessionTest {
    private fun lessons(n: Int)=(1..n).map { Lesson("$it","business","beginner","meeting","I have a meeting.","회의가 있어요.") }
    @Test fun stopsAtRequestedCountWithoutCycling() {
        val p=CountedSession(lessons(6),3)
        repeat(3) { assertNotNull(p.current); assertTrue(p.completeCurrent()); p.advance() }
        assertTrue(p.finished); assertNull(p.current); assertEquals(3,p.completedCount)
        p.advance(); assertEquals(3,p.position)
    }
    @Test fun replayDoesNotInflateCount() {
        val p=CountedSession(lessons(5),5)
        assertTrue(p.completeCurrent()); assertFalse(p.completeCurrent()); assertEquals(1,p.completedCount)
        assertEquals(0,p.position)
    }
    @Test fun skipNeverReceivesListeningCredit() {
        val p=CountedSession(lessons(3),3)
        p.advance(skip=true); p.completeCurrent(); p.advance(skip=true); p.advance(skip=true)
        assertTrue(p.finished); assertEquals(1,p.completedCount); assertEquals(2,p.skippedCount)
    }
    @Test fun availableExpressionsCapCountAndRemoveDuplicates() {
        val items=lessons(2)
        val p=CountedSession(items+items,10)
        assertEquals(2,p.total)
        assertTrue(CountedSession(emptyList(),5).finished)
    }
    @Test fun silenceWavContainsTwoSecondsOfPcm() {
        val f=File.createTempFile("drive-pause",".wav")
        try {
            writeSilenceWav(f,2)
            val bytes=f.readBytes()
            assertEquals("RIFF",String(bytes,0,4)); assertEquals(64044,bytes.size)
            assertEquals(64000,ByteBuffer.wrap(bytes,40,4).order(ByteOrder.LITTLE_ENDIAN).int)
            assertTrue(bytes.drop(44).all { it==0.toByte() })
        } finally { f.delete() }
    }
}
