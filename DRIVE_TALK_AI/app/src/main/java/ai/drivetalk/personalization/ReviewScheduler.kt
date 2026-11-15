package ai.drivetalk.personalization
import ai.drivetalk.learning.*

interface ReviewScheduler { fun rate(progress: Progress, rating: Rating, now: Long): Progress; fun rank(lessons: List<Lesson>, progress: List<Progress>, now: Long, startOfDay: Long): List<Lesson> }
class SpacedReviewScheduler : ReviewScheduler {
    override fun rate(progress: Progress, rating: Rating, now: Long): Progress {
        val days = when(rating) { Rating.AGAIN -> 0; Rating.HARD -> 1; Rating.GOOD -> if(progress.intervalDays < 1) 1 else (progress.intervalDays * 2).coerceAtMost(30) }
        return progress.copy(correct = progress.correct + if(rating == Rating.GOOD) 1 else 0,
            wrong = progress.wrong + if(rating == Rating.AGAIN) 1 else 0,
            difficult = rating != Rating.GOOD, intervalDays = days, lastReviewed = now,
            due = now + if(rating == Rating.AGAIN) 10 * 60_000L else days * 86_400_000L)
    }
    override fun rank(lessons: List<Lesson>, progress: List<Progress>, now: Long, startOfDay: Long): List<Lesson> {
        val index = progress.associateBy { it.lessonId }
        return lessons.filter { index[it.id]?.let { p -> p.listens > 0 || p.lastReviewed > 0 } == true }.sortedWith(
            compareByDescending<Lesson> { index[it.id]?.difficult == true }
                .thenByDescending { (index[it.id]?.due ?: Long.MAX_VALUE) <= now }
                .thenByDescending { (index[it.id]?.lastListened ?: 0) >= startOfDay }
                .thenBy { index[it.id]?.due ?: Long.MAX_VALUE })
    }
}
enum class VoiceCommand { REPEAT, NEXT, STOP, PAUSE, RESUME, UNKNOWN }
fun parseCommand(input: String): VoiceCommand = when(input.trim().lowercase().replace(Regex("[.!?]"), "")) {
    "repeat", "반복", "다시" -> VoiceCommand.REPEAT
    "next", "다음", "건너뛰기" -> VoiceCommand.NEXT
    "stop", "종료", "그만" -> VoiceCommand.STOP
    "pause", "일시정지" -> VoiceCommand.PAUSE
    "resume", "계속", "재개" -> VoiceCommand.RESUME
    else -> VoiceCommand.UNKNOWN
}
