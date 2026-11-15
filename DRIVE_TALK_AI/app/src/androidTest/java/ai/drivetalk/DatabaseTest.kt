package ai.drivetalk
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room
import ai.drivetalk.data.*
import ai.drivetalk.learning.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    @Test fun recordsSurviveReopenAndListeningIsNotCorrect() = runBlocking<Unit> {
        val c=InstrumentationRegistry.getInstrumentation().targetContext
        c.deleteDatabase("verification.db")
        var db=Room.databaseBuilder(c,LearningDatabase::class.java,"verification.db").build()
        var r=RoomUserProgressRepository(db)
        r.listened("test",100); r.listened("test",200)
        var p=r.all().single(); Assert.assertEquals(2,p.listens); Assert.assertEquals(0,p.correct); Assert.assertEquals(0,p.wrong); Assert.assertEquals(0L,p.due)
        r.rate("test",Rating.AGAIN,300); db.close()
        db=Room.databaseBuilder(c,LearningDatabase::class.java,"verification.db").build(); r=RoomUserProgressRepository(db)
        p=r.all().single(); Assert.assertEquals(2,p.listens); Assert.assertTrue(p.difficult); Assert.assertEquals(600300L,p.due)
        r.clear(); Assert.assertTrue(r.all().isEmpty()); db.close(); c.deleteDatabase("verification.db")
    }
}
