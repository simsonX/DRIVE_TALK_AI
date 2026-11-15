package ai.drivetalk.audio
import android.content.ComponentName
import android.content.Context
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AudioPlaybackController { val playing: MutableStateFlow<Boolean>; suspend fun play(files: List<File>, title: String); fun pause(); fun resume(); fun stop(); fun close() }
class Media3AudioPlaybackController(context: Context) : AudioPlaybackController {
    override val playing = MutableStateFlow(false)
    private val future = MediaController.Builder(context,SessionToken(context, ComponentName(context, LearningMediaService::class.java))).buildAsync()
    private suspend fun controller(): MediaController = suspendCancellableCoroutine { c -> future.addListener({ try { if(c.isActive) c.resume(future.get()) } catch(e: Exception) { if(c.isActive) c.resumeWithException(e) } }, { it.run() }) }
    override suspend fun play(files: List<File>, title: String) {
        val p = controller()
        android.util.Log.d("DriveAudio", "Preparing ${files.map { it.exists() to it.length() }}")
        try {
            suspendCancellableCoroutine<Unit> { c ->
                val listener = object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playing.value = isPlaying }
                    override fun onPlaybackStateChanged(state: Int) { android.util.Log.d("DriveAudio", "Playback state $state"); if(state == Player.STATE_ENDED && c.isActive) c.resume(Unit) }
                    override fun onPlayerError(error: PlaybackException) { if(c.isActive) c.resumeWithException(error) }
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if(!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) playing.value = false
                    }
                }
                p.addListener(listener)
                p.setMediaItems(files.map { MediaItem.Builder().setUri(android.net.Uri.fromFile(it)).setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist("DRIVE TALK AI").build()).build() })
                p.prepare(); p.play()
                c.invokeOnCancellation { p.removeListener(listener); p.stop() }
                // Removed after completion below; cancellation also unregisters it.
                activeListener = listener
            }
        } finally { activeListener?.let { p.removeListener(it) }; activeListener = null; playing.value = false }
    }
    private var activeListener: Player.Listener? = null
    private fun use(block: (MediaController) -> Unit) { if(future.isDone && !future.isCancelled) runCatching { block(future.get()) } }
    override fun pause() = use { it.pause() }
    override fun resume() = use { it.play() }
    override fun stop() { use { it.stop(); it.clearMediaItems() }; playing.value=false }
    override fun close() { stop(); MediaController.releaseFuture(future) }
}
