package ai.drivetalk
import ai.drivetalk.learning.*
import ai.drivetalk.personalization.*
import ai.drivetalk.vehicle.*
import org.junit.Assert.*
import org.junit.Test
class CoreTest {
    private val scheduler=SpacedReviewScheduler()
    @Test fun goodIntervalsDoubleWithCap() { var p=Progress("a"); listOf(1,2,4,8,16,30,30).forEach { days -> p=scheduler.rate(p,Rating.GOOD,100); assertEquals(days,p.intervalDays); assertEquals(100+days*86400000L,p.due) } }
    @Test fun againSchedulesTenMinutes() { val p=scheduler.rate(Progress("a",intervalDays=16),Rating.AGAIN,1000); assertEquals(601000L,p.due); assertEquals(1,p.wrong); assertEquals(0,p.correct); assertTrue(p.difficult) }
    @Test fun hardDoesNotCountAsWrong() { val p=scheduler.rate(Progress("a"),Rating.HARD,0); assertEquals(0,p.correct); assertEquals(0,p.wrong); assertEquals(86400000L,p.due) }
    @Test fun difficultPrioritized() { val a=Lesson("a","business","beginner","meeting","I have a meeting.","회의가 있어요."); val b=a.copy(id="b"); assertEquals("b",scheduler.rank(listOf(a,b),listOf(Progress("a",listens=1),Progress("b",listens=1,difficult=true)),100,0).first().id) }
    @Test fun emptyReviewStaysEmpty() { assertTrue(scheduler.rank(listOf(Lesson("a","b","c","d","e","f")), emptyList(),0,0).isEmpty()) }
    @Test fun commandsAreExact() { assertEquals(VoiceCommand.REPEAT,parseCommand(" Repeat! ")); assertEquals(VoiceCommand.STOP,parseCommand("종료")); assertEquals(VoiceCommand.UNKNOWN,parseCommand("do not stop")); assertEquals(VoiceCommand.NEXT,parseCommand("다음")) }
    @Test fun unknownAndDrivingFailClosed() { assertFalse(VehicleStatus().parkedFeaturesAllowed); assertFalse(VehicleStatus(VehicleState.DRIVING,true).parkedFeaturesAllowed); assertTrue(VehicleStatus(VehicleState.PARKED,true).parkedFeaturesAllowed) }
    @Test fun demoDoesNotPersistOrStartParked() { val v=DemoVehicleStateProvider(); assertEquals(VehicleState.UNKNOWN,v.status.value.state); v.simulate(VehicleState.PARKED); assertTrue(v.status.value.demo); v.reset(); assertFalse(v.status.value.parkedFeaturesAllowed) }
}
