package ai.drivetalk
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ai.drivetalk.ui.DriveViewModel
import ai.drivetalk.vehicle.VehicleState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CountedPlaybackTest {
    @Test fun countSessionWaitsPausesAndFinishesWithoutLooping() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        fun waitUntil(timeout: Long=45000, condition: ()->Boolean) {
            val until=SystemClock.elapsedRealtime()+timeout
            while(!condition() && SystemClock.elapsedRealtime()<until) Thread.sleep(100)
            assertTrue("Timed out waiting for counted session",condition())
        }
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext,MainActivity::class.java)).use { scenario ->
            lateinit var model: DriveViewModel
            scenario.onActivity { model=ViewModelProvider(it)[DriveViewModel::class.java]; model.simulate(VehicleState.PARKED) }
            instrumentation.waitForIdleSync()
            val oldCount=model.ui.value.lessonCount
            val oldGap=model.ui.value.gapSeconds
            scenario.onActivity { model.settings(lessonCount=3,gapSeconds=8); model.start() }
            waitUntil { model.ui.value.waiting }
            assertEquals(3,model.ui.value.sessionTotal); assertEquals(1,model.ui.value.completedCount)
            val firstId=model.ui.value.lesson!!.id
            scenario.onActivity { model.pause() }
            Thread.sleep(200)
            val countdown=model.ui.value.nextInSeconds
            Thread.sleep(1800)
            assertEquals(countdown,model.ui.value.nextInSeconds)
            assertEquals(firstId,model.ui.value.lesson!!.id)
            scenario.onActivity { model.resume() }
            waitUntil(18000) { model.ui.value.currentNumber==2 }
            // Complete the finite session by explicitly skipping the remaining two items.
            scenario.onActivity { model.next(); model.next() }
            waitUntil(3000) { !model.ui.value.active }
            assertEquals(1,model.ui.value.completedCount)
            assertEquals(2,model.ui.value.skippedCount)
            assertTrue(model.ui.value.status.startsWith("학습 완료"))
            scenario.onActivity { model.settings(lessonCount=oldCount,gapSeconds=oldGap); model.disableDemo() }
        }
    }
}
