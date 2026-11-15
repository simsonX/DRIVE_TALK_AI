package ai.drivetalk.learning

/** A finite list of distinct expressions. Repeats never consume an extra slot. */
class CountedSession(lessons: List<Lesson>, requestedCount: Int) {
    init { require(requestedCount > 0) }
    val lessons = lessons.distinctBy { it.id }.take(requestedCount)
    var position = 0; private set
    private val completed = mutableSetOf<Int>()
    private val skipped = mutableSetOf<Int>()
    val total get() = lessons.size
    val completedCount get() = completed.size
    val skippedCount get() = skipped.size
    val finished get() = position >= total
    val current get() = lessons.getOrNull(position)
    fun completeCurrent(): Boolean = !finished && completed.add(position)
    fun advance(skip: Boolean = false) {
        if(finished) return
        if(skip && position !in completed) skipped.add(position)
        position++
    }
}
