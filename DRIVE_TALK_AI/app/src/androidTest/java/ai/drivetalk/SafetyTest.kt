package ai.drivetalk
import android.content.Intent
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
class SafetyTest {
    @Test fun drivingRevokesConversationAndRejectsParkedActions() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext,MainActivity::class.java)).use { scenario ->
            scenario.onActivity { a -> ViewModelProvider(a)[DriveViewModel::class.java].simulate(VehicleState.PARKED) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { a -> val vm=ViewModelProvider(a)[DriveViewModel::class.java]; vm.tab("talk"); assertEquals("talk",vm.ui.value.tab); vm.simulate(VehicleState.DRIVING) }
            instrumentation.waitForIdleSync()
            scenario.onActivity { a ->
                val vm=ViewModelProvider(a)[DriveViewModel::class.java]
                assertEquals("home",vm.ui.value.tab)
                vm.tab("talk"); vm.startConversation(); vm.generate(); vm.answer("hello")
                assertEquals("home",vm.ui.value.tab); assertFalse(vm.ui.value.busy); assertTrue(vm.ui.value.conversation.isEmpty())
                vm.disableDemo()
            }
        }
    }
}
