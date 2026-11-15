package ai.drivetalk.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ai.drivetalk.learning.*
import ai.drivetalk.personalization.SpacedReviewScheduler
import org.json.JSONArray

@Entity(primaryKeys = ["userId", "lessonId"])
data class ProgressEntity(val userId: String = "local", val lessonId: String, val listens: Int = 0, val correct: Int = 0, val wrong: Int = 0, val difficult: Boolean = false, val intervalDays: Int = 0, val lastListened: Long = 0, val lastReviewed: Long = 0, val due: Long = 0) {
    fun model() = Progress(lessonId,listens,correct,wrong,difficult,intervalDays,lastListened,lastReviewed,due)
}
@Entity data class EventEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val userId: String = "local", val lessonId: String, val kind: String, val time: Long)
@Entity data class GeneratedEntity(@PrimaryKey val id: String, val category: String, val level: String, val expression: String, val english: String, val korean: String, val source: String)
@Entity data class SessionEntity(@PrimaryKey val id: String, val userId: String = "local", val category: String, val level: String, val mode: String, val started: Long, val ended: Long = 0, val listenedSeconds: Int = 0)
@Dao interface LearningDao {
    @Insert suspend fun startSession(value: SessionEntity)
    @Query("UPDATE SessionEntity SET listenedSeconds = :seconds, ended = :ended WHERE id = :id") suspend fun updateSession(id: String, seconds: Int, ended: Long = 0)
    @Query("SELECT COALESCE(SUM(listenedSeconds), 0) FROM SessionEntity WHERE userId = :user") suspend fun totalSeconds(user: String = "local"): Int
    @Query("DELETE FROM SessionEntity") suspend fun clearSessions()
    @Query("SELECT * FROM ProgressEntity WHERE userId = :user") suspend fun all(user: String = "local"): List<ProgressEntity>
    @Query("SELECT * FROM ProgressEntity WHERE userId = :user AND lessonId = :id") suspend fun get(id: String, user: String = "local"): ProgressEntity?
    @Upsert suspend fun put(value: ProgressEntity)
    @Insert suspend fun event(value: EventEntity)
    @Upsert suspend fun generated(value: GeneratedEntity)
    @Query("SELECT * FROM GeneratedEntity WHERE category = :category AND level = :level") suspend fun generated(category: String, level: String): List<GeneratedEntity>
    @Query("DELETE FROM ProgressEntity") suspend fun clearProgress()
    @Query("DELETE FROM EventEntity") suspend fun clearEvents()
    @Query("DELETE FROM GeneratedEntity") suspend fun clearGenerated()
}
@Database(entities = [ProgressEntity::class, EventEntity::class, GeneratedEntity::class, SessionEntity::class], version = 2, exportSchema = true)
abstract class LearningDatabase : RoomDatabase() { abstract fun dao(): LearningDao
    companion object {
        val MIGRATION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS `SessionEntity` (`id` TEXT NOT NULL, `userId` TEXT NOT NULL, `category` TEXT NOT NULL, `level` TEXT NOT NULL, `mode` TEXT NOT NULL, `started` INTEGER NOT NULL, `ended` INTEGER NOT NULL, `listenedSeconds` INTEGER NOT NULL, PRIMARY KEY(`id`))") }
        }
        fun open(context: Context) = Room.databaseBuilder(context, LearningDatabase::class.java, "drive-talk.db").addMigrations(MIGRATION_1_2).build()
    }
}
class RoomUserProgressRepository(private val db: LearningDatabase) : UserProgressRepository {
    private val dao = db.dao()
    override suspend fun all() = dao.all().map { it.model() }
    override suspend fun listened(id: String, now: Long) = db.withTransaction {
        val p = dao.get(id) ?: ProgressEntity(lessonId = id)
        dao.put(p.copy(listens = p.listens + 1, lastListened = now))
        dao.event(EventEntity(lessonId = id, kind = "LISTEN", time = now))
    }
    override suspend fun rate(id: String, rating: Rating, now: Long) = db.withTransaction {
        val p = SpacedReviewScheduler().rate((dao.get(id) ?: ProgressEntity(lessonId=id)).model(), rating, now)
        dao.put(ProgressEntity(lessonId=id,listens=p.listens,correct=p.correct,wrong=p.wrong,difficult=p.difficult,intervalDays=p.intervalDays,lastListened=p.lastListened,lastReviewed=p.lastReviewed,due=p.due))
        dao.event(EventEntity(lessonId = id, kind = rating.name, time = now))
    }
    override suspend fun clear() = db.withTransaction { dao.clearEvents(); dao.clearProgress(); dao.clearGenerated(); dao.clearSessions() }
}
class AssetLearningContentRepository(private val context: Context, private val dao: LearningDao) : LearningContentRepository {
    val basic: List<Lesson> by lazy {
        val rows = JSONArray(context.assets.open("lessons/lessons.json").bufferedReader().use { it.readText() })
        (0 until rows.length()).map { val j=rows.getJSONObject(it); Lesson(j.getString("id"),j.getString("category"),j.getString("level"),j.getString("expression"),j.getString("english"),j.getString("korean")) }
    }
    override suspend fun lessons(category: String, level: String) = basic.filter { it.category == category && it.level == level } + dao.generated(category,level).map { Lesson(it.id,it.category,it.level,it.expression,it.english,it.korean,it.source) }
    override suspend fun saveGenerated(lesson: Lesson) { dao.generated(GeneratedEntity(lesson.id,lesson.category,lesson.level,lesson.expression,lesson.english,lesson.korean,lesson.source)) }
}
