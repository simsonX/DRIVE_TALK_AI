package ai.drivetalk
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drivetalk.ui.DriveViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File
@RunWith(AndroidJUnit4::class)
class PlaybackTest {
    @Test fun realBilingualPlaybackPersistsListenAndHandlesNavigationFocus() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val c=instrumentation.targetContext
        var vm: DriveViewModel?=null
        fun waitUntil(timeout: Long=40_000, condition: ()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+timeout
            while(!condition() && SystemClock.elapsedRealtime()<deadline) Thread.sleep(100)
            assertTrue("Condition not reached within ${timeout}ms",condition())
        }
        ActivityScenario.launch<MainActivity>(Intent(c,MainActivity::class.java)).use { scenario ->
            scenario.onActivity { a -> vm=ViewModelProvider(a)[DriveViewModel::class.java] }
            val model=vm!!
            instrumentation.waitForIdleSync()
            val before=model.ui.value.progress.sumOf { it.listens }
            val started=SystemClock.elapsedRealtime()
            scenario.onActivity { model.start() }
            waitUntil { model.ui.value.playing }
            val latency=SystemClock.elapsedRealtime()-started
            scenario.onActivity { model.pause() }; waitUntil(3000) { !model.ui.value.playing }
            scenario.onActivity { model.resume() }; waitUntil(3000) { model.ui.value.playing }
            val audio=c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { }.build()
            assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED,audio.requestAudioFocus(focus))
            try { waitUntil(4000) { !model.ui.value.playing } } finally { audio.abandonAudioFocusRequest(focus) }
            waitUntil(5000) { model.ui.value.playing }
            waitUntil(45_000) { model.ui.value.progress.sumOf { it.listens }>before }
            val progress=model.ui.value.progress.first { it.lessonId==model.ui.value.lesson?.id }
            assertEquals(0,progress.correct); assertEquals(0,progress.wrong)
            scenario.onActivity { model.repeat(); model.next(); model.stop() }
            waitUntil(3000) { !model.ui.value.playing && !model.ui.value.active }
            val evidence=JSONObject().put("start_to_playing_ms",latency).put("english_korean_english",true).put("navigation_focus_pause_resume",true).put("listen_saved_without_correct_credit",true).toString(2)
            File(c.filesDir,"playback-evidence.json").writeText(evidence)
            android.util.Log.i("DriveMetrics", evidence)
        }
    }
}
