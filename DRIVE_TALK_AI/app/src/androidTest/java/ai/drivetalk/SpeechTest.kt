package ai.drivetalk
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drivetalk.speech.AndroidSpeechSynthesisService
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class SpeechTest {
    @Test fun actualEnglishAndKoreanSynthesisProduceAudio() = runBlocking<Unit> {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        withContext(Dispatchers.Main) {
            val tts=AndroidSpeechSynthesisService(c)
            try {
                val english=tts.synthesize("I have a meeting today.","en-US")
                Assert.assertTrue("English audio must contain samples",english.length()>100)
                val korean=tts.synthesize("오늘 회의가 있어요.","ko-KR")
                Assert.assertTrue("Korean audio must contain samples",korean.length()>100)
                english.delete(); korean.delete()
            } finally { tts.close() }
        }
    }
}
