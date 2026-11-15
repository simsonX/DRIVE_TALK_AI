package ai.drivetalk.learning

data class Lesson(val id: String, val category: String, val level: String, val expression: String, val english: String, val korean: String, val source: String = "검수 기본 콘텐츠")
data class Progress(val lessonId: String, val listens: Int = 0, val correct: Int = 0, val wrong: Int = 0, val difficult: Boolean = false, val intervalDays: Int = 0, val lastListened: Long = 0, val lastReviewed: Long = 0, val due: Long = 0)
enum class Rating { AGAIN, HARD, GOOD }
interface LearningContentRepository { suspend fun lessons(category: String, level: String): List<Lesson>; suspend fun saveGenerated(lesson: Lesson) }
interface UserProgressRepository { suspend fun all(): List<Progress>; suspend fun listened(id: String, now: Long); suspend fun rate(id: String, rating: Rating, now: Long); suspend fun clear() }
