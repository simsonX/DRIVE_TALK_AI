package ai.drivetalk
import ai.drivetalk.ai.*
import ai.drivetalk.data.LearningDatabase
import ai.drivetalk.learning.Lesson
import ai.drivetalk.ui.DriveViewModel
import ai.drivetalk.vehicle.VehicleState
import android.os.SystemClock
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegenerationTest {
    private class TestInference(var failFirst: Boolean=false, var duplicate: Boolean=false) : AIInferenceService {
        var calls=0
        private val nonce=java.util.UUID.randomUUID().toString().take(8)
        override suspend fun generate(level: String, category: String, expression: String, previousExamples: List<String>): Lesson {
            calls++; delay(250)
            if(failFirst && calls==1) error("TEST_ONLY simulated offline")
            val en=if(duplicate || calls==1) "We can review test $nonce today." else "Please confirm meeting $nonce tomorrow."
            return Lesson("ai-regression-${java.util.UUID.randomUUID()}",category,level,expression,en,"검증용 문장입니다.","TEST_ONLY")
        }
        override suspend fun question(level: String, turn: Int): String = error("unused")
        override suspend fun feedback(answer: String, language: String): Feedback = error("unused")
    }
    private fun waitFor(condition: ()->Boolean) {
        val until=SystemClock.elapsedRealtime()+45000
        while(!condition() && SystemClock.elapsedRealtime()<until) Thread.sleep(50)
        assertTrue("Regeneration did not finish",condition())
    }
    private fun withModel(ai: AIInferenceService, verify: (DriveViewModel, (()->Unit)->Unit)->Unit) {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val store=ViewModelStore()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: DriveViewModel
            scenario.onActivity { model=DriveViewModel(it.application,ai); store.put("regression",model); model.simulate(VehicleState.PARKED) }
            instrumentation.waitForIdleSync()
            waitFor { model.ui.value.lesson!=null && model.ui.value.vehicle.parkedFeaturesAllowed }
            try { verify(model) { action -> scenario.onActivity { action() } } }
            finally { scenario.onActivity { model.stop(); store.clear() } }
        }
        runBlocking(Dispatchers.IO) {
            val db=LearningDatabase.open(instrumentation.targetContext)
            db.openHelper.writableDatabase.execSQL("DELETE FROM GeneratedEntity WHERE id LIKE 'ai-regression-%'")
            db.close()
        }
    }
    @Test fun secondGenerationFinishesWithNewContentAndUnlocks() {
        withModel(TestInference()) { model,onMain ->
            onMain { model.generate() }; waitFor { !model.ui.value.generating }
            assertEquals(1,model.ui.value.generatedCount); val first=model.ui.value.lesson!!.english
            onMain { model.generate() }; waitFor { !model.ui.value.generating }
            assertEquals(2,model.ui.value.generatedCount); assertNotEquals(first,model.ui.value.lesson!!.english)
            assertFalse(model.ui.value.busy)
        }
    }
    @Test fun failureAllowsAnotherAttemptAndPreservesCurrentSentence() {
        withModel(TestInference(failFirst=true)) { model,onMain ->
            val first=model.ui.value.lesson
            onMain { model.generate() }; waitFor { !model.ui.value.generating }
            assertEquals(first,model.ui.value.lesson); assertEquals(0,model.ui.value.generatedCount)
            onMain { model.generate() }; waitFor { !model.ui.value.generating }
            assertEquals(1,model.ui.value.generatedCount); assertFalse(model.ui.value.busy)
        }
    }
    @Test fun duplicateDoesNotPretendToBeNewContent() {
        withModel(TestInference(duplicate=true)) { model,onMain ->
            onMain { model.generate() }; waitFor { !model.ui.value.generating }; val first=model.ui.value.lesson
            onMain { model.generate() }; waitFor { !model.ui.value.generating }
            assertEquals(1,model.ui.value.generatedCount); assertEquals(first,model.ui.value.lesson); assertFalse(model.ui.value.busy)
        }
    }
    @Test fun cancelledRequestCannotUnlockTheNextRequest() {
        withModel(TestInference()) { model,onMain ->
            onMain { model.generate(); model.stop(); model.generate() }
            Thread.sleep(100); assertTrue(model.ui.value.generating)
            waitFor { !model.ui.value.generating }; assertEquals(1,model.ui.value.generatedCount)
        }
    }
    @Test fun realQwenGeneratesThreeDifferentSentences() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveAi")=="true")
        withModel(LocalQwenInferenceService()) { model,onMain ->
            val sentences=mutableSetOf<String>()
            repeat(3) { index ->
                onMain { model.generate() }; waitFor { !model.ui.value.generating }
                assertEquals(index+1,model.ui.value.generatedCount)
                assertTrue(model.ui.value.lesson!!.source.startsWith("Qwen"))
                assertTrue("Qwen repeated a sentence",sentences.add(model.ui.value.lesson!!.english))
                println("REAL_QWEN_REGEN_${index+1}: ${model.ui.value.lesson!!.english}")
            }
        }
    }
}
