package ai.drivetalk.ai
import ai.drivetalk.learning.Lesson
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

interface AIInferenceService { suspend fun generate(level: String, category: String, expression: String, previousExamples: List<String> = emptyList()): Lesson; suspend fun question(level: String, turn: Int): String; suspend fun feedback(answer: String, language: String): Feedback }
data class Feedback(val corrected: String, val explanation: String, val uncertain: Boolean)
class LocalQwenInferenceService(private val endpoint: String = "http://10.0.2.2:8765") : AIInferenceService {
    private suspend fun request(path: String, body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        var failure: Exception? = null
        repeat(1) {
            val connection = URL(endpoint+path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod="POST"; connection.connectTimeout=2500; connection.readTimeout=20000; connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                if(connection.responseCode != 200) error("로컬 Qwen 서버 오류 (${connection.responseCode})")
                val text = connection.inputStream.bufferedReader().use { it.readText().also { value -> require(value.length < 16384) } }
                return@withContext JSONObject(text).also { require(it.getString("source").startsWith("qwen")) }
            } catch(e: Exception) { failure=e } finally { connection.disconnect() }
        }
        throw failure ?: IllegalStateException("AI 연결 실패")
    }
    override suspend fun generate(level: String, category: String, expression: String, previousExamples: List<String>): Lesson {
        val j = request("/generate", JSONObject().put("level",level).put("category",category).put("expression",expression).put("previous_examples",org.json.JSONArray(previousExamples.takeLast(6))))
        val en=j.getString("english"); val ko=j.getString("korean")
        require(en.length in 5..300 && en.any { it in 'a'..'z' } && ko.length in 2..500 && ko.any { it in '가'..'힣' })
        return Lesson("ai-${java.util.UUID.randomUUID()}",category,level,expression,en,ko,"Qwen 실제 생성 · 언어 정확성 미검수")
    }
    override suspend fun question(level: String, turn: Int) = request("/question",JSONObject().put("level",level).put("turn",turn)).getString("question").also { require(it.length in 5..300) }
    override suspend fun feedback(answer: String, language: String): Feedback {
        val j=request("/feedback",JSONObject().put("answer",answer.take(500)).put("language",language))
        return Feedback(j.getString("corrected"),j.getString("explanation"),j.getBoolean("uncertain"))
    }
}
