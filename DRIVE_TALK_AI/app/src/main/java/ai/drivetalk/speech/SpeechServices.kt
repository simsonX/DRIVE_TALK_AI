package ai.drivetalk.speech
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface SpeechSynthesisService { val implementation: String; suspend fun synthesize(text: String, language: String): File; fun close() }
interface SpeechRecognitionService { val implementation: String; fun listen(language: String, result: (String) -> Unit, error: (String) -> Unit); fun stop(); fun close() }
class AndroidSpeechSynthesisService(private val context: Context) : SpeechSynthesisService {
    override val implementation = "Android TTS 대체 구현 · Pleos 미연동"
    private val ready = CompletableDeferred<Unit>()
    private val tts = TextToSpeech(context) { code -> if(code == TextToSpeech.SUCCESS) ready.complete(Unit) else ready.completeExceptionally(IllegalStateException("TTS 초기화 실패: $code")) }
    override suspend fun synthesize(text: String, language: String): File = withTimeout(25_000) {
        ready.await()
        val locale = Locale.forLanguageTag(language)
        if(tts.isLanguageAvailable(locale) < TextToSpeech.LANG_AVAILABLE) error("$language 음성 데이터가 없습니다. Android TTS 설정에서 설치해 주세요.")
        val file = File(context.cacheDir, "speech-${java.util.UUID.randomUUID()}.wav")
        suspendCancellableCoroutine { continuation ->
            val id = file.name
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) { if(utteranceId == id && continuation.isActive) continuation.resume(file) }
                @Deprecated("API") override fun onError(utteranceId: String?) { if(continuation.isActive) continuation.resumeWithException(IllegalStateException("음성 합성 실패")) }
            })
            tts.language = locale
            tts.setSpeechRate(0.78f)
            if(tts.synthesizeToFile(text, Bundle(), file, id) == TextToSpeech.ERROR && continuation.isActive) continuation.resumeWithException(IllegalStateException("음성 합성 요청 실패"))
            continuation.invokeOnCancellation { tts.stop(); file.delete() }
        }
    }
    override fun close() { tts.stop(); tts.shutdown() }
}
class AndroidSpeechRecognitionService(private val context: Context) : SpeechRecognitionService {
    override val implementation = "Android STT 대체 구현 · 실제 마이크 · 기기/네트워크에 의존"
    private var recognizer: SpeechRecognizer? = null
    override fun listen(language: String, result: (String) -> Unit, error: (String) -> Unit) {
        stop()
        if(!SpeechRecognizer.isRecognitionAvailable(context)) { error("음성 인식 서비스가 없습니다. 주차 중 텍스트 입력을 사용할 수 있습니다."); return }
        val r = SpeechRecognizer.createSpeechRecognizer(context); recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(code: Int) { error("음성 인식 실패 ($code). 다시 시도해 주세요.") }
            override fun onResults(results: Bundle?) { results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(result) ?: error("인식된 음성이 없습니다.") }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE,language).putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1))
    }
    override fun stop() { recognizer?.cancel(); recognizer?.destroy(); recognizer = null }
    override fun close() = stop()
}
