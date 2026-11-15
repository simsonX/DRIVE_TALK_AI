package ai.drivetalk.pleos
import android.content.Context
import java.util.Locale
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import ai.pleos.playground.tts.listener.EventListener

/** Compile-checked SDK bridge, intentionally NOT enabled in the demo APK.
 * Pleos TTS streams directly to the vehicle: it does not expose synthesizeToFile.
 * Before wiring this bridge into the app, integrate its focus/interruption lifecycle
 * and verify both locales on an actual Pleos Speech Service.
 */
class PleosSpeechBridge(context: Context, private val onError: (String)->Unit) {
    private val stt=SpeechToText(context,SttMode.ON_DEVICE)
    private val tts=TextToSpeech(context,TtsMode.ON_DEVICE)
    var recognitionReady=false; private set
    var synthesisReady=false; private set
    private var result: ((String)->Unit)?=null
    private var done: (()->Unit)?=null
    private val recognitionListener=object : ResultListener {
        override fun onReady() {}
        override fun onStartedRecognition() {}
        override fun onUpdated(stt: String, completed: Boolean) { if(completed) result?.invoke(stt) }
        override fun onUpdatedEpdData(on: Long, off: Long) {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onEndedRecognition() {}
        override fun onError(errorType: SttError) { onError("Pleos STT: $errorType") }
    }
    private val synthesisListener=object : EventListener {
        override fun onReady() {}
        override fun onStart() {}
        override fun onDone() { done?.invoke() }
        override fun onStop() {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onError(errorType: TtsError) { onError("Pleos TTS: $errorType") }
    }
    fun initialize() {
        stt.initialize(object : SttInit {
            override fun onSuccess() { recognitionReady=true; stt.addListener(recognitionListener) }
            override fun onFailure(errorType: SttError) { recognitionReady=false; onError("Pleos STT init: $errorType") }
        })
        tts.initialize(object : TtsInit {
            override fun onSuccess() { synthesisReady=true; tts.addEventListener(synthesisListener) }
            override fun onFailure(errorType: TtsError) { synthesisReady=false; onError("Pleos TTS init: $errorType") }
        })
    }
    fun recognize(locale: Locale, onResult: (String)->Unit) { check(recognitionReady); result=onResult; stt.setLocale(locale); stt.request() }
    fun speak(text: String, locale: Locale, onDone: ()->Unit) { check(synthesisReady); done=onDone; tts.setLocale(locale); tts.speak(text,true) }
    fun stop() { stt.stop(); tts.stop(); result=null; done=null }
    fun close() { stop(); stt.removeListener(recognitionListener); tts.removeEventListener(synthesisListener); stt.release(); tts.release(); recognitionReady=false; synthesisReady=false }
}
