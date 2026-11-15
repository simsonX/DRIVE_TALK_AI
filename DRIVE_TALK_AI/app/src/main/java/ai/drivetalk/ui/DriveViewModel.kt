package ai.drivetalk.ui
import android.app.Application
import ai.drivetalk.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ai.drivetalk.learning.*
import ai.drivetalk.data.*
import ai.drivetalk.personalization.*
import ai.drivetalk.vehicle.*
import ai.drivetalk.audio.*
import ai.drivetalk.speech.*
import ai.drivetalk.ai.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.io.File

data class DriveUiState(
    val level: String="beginner", val category: String="business", val lessonCount: Int=5, val gapSeconds: Int=8,
    val vehicle: VehicleStatus=VehicleStatus(), val lesson: Lesson?=null, val progress: List<Progress> = emptyList(),
    val learnedSeconds: Int=0, val active: Boolean=false, val playing: Boolean=false, val busy: Boolean=false, val completedCount: Int=0, val sessionTotal: Int=0, val currentNumber: Int=0, val skippedCount: Int=0,
    val paused: Boolean=false, val waiting: Boolean=false, val preparing: Boolean=false, val nextInSeconds: Int=0,
    val generatedCount: Int=0, val generating: Boolean=false,
    val status: String="준비 완료 · 오디오를 직접 시작하세요", val aiStatus: String="기본 콘텐츠 · Qwen 미호출",
    val tab: String="home", val conversation: List<String> = emptyList(), val turn: Int=0,
    val explanationLanguage: String="ko", val morningHour: Int=8, val eveningHour: Int=18,
    val voiceStatus: String="Android TTS/STT 대체 구현 · Pleos 미연동"
)
class DriveViewModel @JvmOverloads constructor(app: Application, private val ai: AIInferenceService = LocalQwenInferenceService()) : AndroidViewModel(app) {
    private val db=LearningDatabase.open(app)
    private val content=AssetLearningContentRepository(app,db.dao())
    private val repository=RoomUserProgressRepository(db)
    private val scheduler=SpacedReviewScheduler()
    private val speech=AndroidSpeechSynthesisService(app)
    private val recognition=AndroidSpeechRecognitionService(app)
    private val audio=Media3AudioPlaybackController(app)
    private val vehicle=DemoVehicleStateProvider(BuildConfig.DEBUG && !app.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE))
    private val prefs=app.getSharedPreferences("settings",0)
    private val _ui=MutableStateFlow(DriveUiState(level=prefs.getString("level","beginner")!!,category=prefs.getString("category","business")!!,lessonCount=prefs.getInt("lessonCount",5).coerceIn(1,15),gapSeconds=prefs.getInt("gapSeconds",8).coerceIn(5,12),morningHour=prefs.getInt("morning",8),eveningHour=prefs.getInt("evening",18)))
    val ui=_ui.asStateFlow()
    private var session: Job?=null
    private var timer: Job?=null
    private var aiJob: Job?=null
    private var generationVersion=0L
    private var speechJob: Job?=null
    private var plan: CountedSession?=null
    private var sessionId: String?=null
    private var elapsedSeconds=0
    private var playback: Job?=null
    init {
        viewModelScope.launch { audio.playing.collect { value -> _ui.update { it.copy(playing=value) } } }
        viewModelScope.launch { vehicle.status.collect { status ->
            _ui.update { it.copy(vehicle=status,tab=if(status.parkedFeaturesAllowed) it.tab else "home") }
            if(!status.parkedFeaturesAllowed) { aiJob?.cancel(); speechJob?.cancel(); recognition.stop(); if(!_ui.value.active) audio.stop(); _ui.update { it.copy(busy=false,generating=false,conversation=emptyList(),turn=0) } }
        } }
        viewModelScope.launch { refresh(); _ui.update { it.copy(lesson=content.lessons(it.category,it.level).firstOrNull()) } }
    }
    private suspend fun refresh() { val values=repository.all(); val seconds=db.dao().totalSeconds(); _ui.update { it.copy(progress=values,learnedSeconds=seconds) } }
    fun simulate(state: VehicleState) { vehicle.simulate(state) }
    fun disableDemo() { vehicle.reset() }
    fun tab(value: String) { if(value in listOf("history","talk") && !ui.value.vehicle.parkedFeaturesAllowed) return; _ui.update { it.copy(tab=value) } }
    fun settings(level: String=ui.value.level, category: String=ui.value.category, lessonCount: Int=ui.value.lessonCount, gapSeconds: Int=ui.value.gapSeconds, language: String=ui.value.explanationLanguage, morning: Int=ui.value.morningHour, evening: Int=ui.value.eveningHour) {
        if(!ui.value.vehicle.parkedFeaturesAllowed || ui.value.active) return
        require(lessonCount in listOf(3,5,10,15) && gapSeconds in listOf(5,8,12))
        _ui.update { it.copy(level=level,category=category,lessonCount=lessonCount,gapSeconds=gapSeconds,explanationLanguage=language,morningHour=morning,eveningHour=evening) }
        prefs.edit().putString("level",level).putString("category",category).putInt("lessonCount",lessonCount).putInt("gapSeconds",gapSeconds).putInt("morning",morning).putInt("evening",evening).apply()
        viewModelScope.launch { _ui.update { it.copy(lesson=content.lessons(category,level).firstOrNull()) } }
    }
    fun start(review: Boolean=false) {
        stop()
        session=viewModelScope.launch {
            val s=ui.value
            val lessons=content.lessons(s.category,s.level)
            val day=LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val ordered=if(review) scheduler.rank(lessons,repository.all(),System.currentTimeMillis(),day) else lessons
            val newPlan=CountedSession(ordered,s.lessonCount)
            if(newPlan.total==0) { _ui.update { it.copy(status="복습 기록이 없습니다. 출근길 학습을 먼저 시작하세요.") }; return@launch }
            plan=newPlan
            elapsedSeconds=0
            val id=java.util.UUID.randomUUID().toString(); sessionId=id
            db.dao().startSession(SessionEntity(id=id,category=s.category,level=s.level,mode=if(review) "evening" else "morning",started=System.currentTimeMillis()))
            _ui.update { it.copy(active=true,paused=false,completedCount=0,skippedCount=0,sessionTotal=newPlan.total,currentNumber=1,
                status=if(newPlan.total<s.lessonCount) "준비된 ${newPlan.total}개 문장으로 진행합니다." else if(review) "퇴근길 복습" else "출근길 새 표현") }
            // Time is recorded only as a statistic; it never ends the session.
            timer=viewModelScope.launch {
                while(ui.value.active) {
                    delay(1000)
                    if(ui.value.playing) { elapsedSeconds++; db.dao().updateSession(id,elapsedSeconds) }
                }
            }
            playCurrent()
        }
    }
    private fun updateCounts(p: CountedSession) {
        _ui.update { it.copy(completedCount=p.completedCount,skippedCount=p.skippedCount,currentNumber=(p.position+1).coerceAtMost(p.total)) }
    }
    private suspend fun awaitUnpaused() { ui.first { !it.paused } }
    private suspend fun thinkingPause(seconds: Int) {
        _ui.update { it.copy(waiting=true,nextInSeconds=seconds,status="따라 말해 보세요 · 다음 문장까지 여유 시간") }
        var remainingMs=seconds*1000L
        while(remainingMs>0) {
            awaitUnpaused()
            delay(100)
            if(!ui.value.paused) {
                remainingMs-=100
                _ui.update { it.copy(nextInSeconds=((remainingMs+999)/1000).toInt()) }
            }
        }
        _ui.update { it.copy(waiting=false,nextInSeconds=0) }
    }
    private fun finishSession() {
        val p=plan ?: return
        val message=if(p.skippedCount>0) "학습 완료 · ${p.completedCount}개 청취, ${p.skippedCount}개 건너뜀" else "학습 완료 · ${p.completedCount}개 문장을 모두 들었어요"
        stop()
        _ui.update { it.copy(status=message) }
    }
    private fun playCurrent() {
        playback?.cancel()
        playback=viewModelScope.launch {
            val p=plan ?: return@launch
            while(isActive && ui.value.active && !p.finished) {
                val lesson=p.current ?: break
                updateCounts(p)
                _ui.update { it.copy(lesson=lesson,waiting=false,nextInSeconds=0,preparing=true,status="음성 준비 중…") }
                try {
                    speak(listOf(lesson.english to "en-US",lesson.korean to "ko-KR",lesson.english to "en-US"),lesson.english, paced=true)
                    if(p.completeCurrent()) { repository.listened(lesson.id,System.currentTimeMillis()); refresh() }
                    updateCounts(p)
                    if(p.position==p.total-1) { p.advance(); break }
                    thinkingPause(ui.value.gapSeconds)
                    p.advance()
                } catch(e: CancellationException) { throw e } catch(e: Exception) {
                    stop(); _ui.update { it.copy(status=e.message ?: "음성 출력 실패") }; return@launch
                }
            }
            if(isActive && ui.value.active && p.finished) finishSession()
        }
    }
    private suspend fun speak(parts: List<Pair<String,String>>, title: String, paced: Boolean=false) {
        val files=mutableListOf<File>()
        try {
            parts.forEachIndexed { index, (text,language) ->
                if(paced && index>0) files += writeSilenceWav(File(getApplication<Application>().cacheDir,"speech-pause-${java.util.UUID.randomUUID()}.wav"),2)
                files += speech.synthesize(text,language)
            }
            awaitUnpaused()
            _ui.update { it.copy(preparing=false,status="재생 중") }
            audio.play(files,title)
        } finally { files.forEach { it.delete() } }
    }
    fun pause() { _ui.update { it.copy(paused=true,status="일시정지 · 재개 버튼으로 계속") }; audio.pause() }
    fun resume() {
        _ui.update { it.copy(paused=false,status=if(it.waiting) "따라 말해 보세요 · 다음 문장까지 여유 시간" else "학습 재개") }
        if(!ui.value.waiting && !ui.value.preparing) audio.resume()
    }
    fun next() {
        if(!ui.value.active) return
        playback?.cancel()
        val p=plan ?: return
        p.advance(skip=true); updateCounts(p)
        _ui.update { it.copy(paused=false,waiting=false,nextInSeconds=0) }
        if(p.finished) finishSession() else playCurrent()
    }
    fun repeat() { if(ui.value.active) { _ui.update { it.copy(paused=false) }; playCurrent() } }
    fun stop() {
        generationVersion++
        val oldId=sessionId; val seconds=elapsedSeconds; sessionId=null
        if(oldId!=null) viewModelScope.launch { db.dao().updateSession(oldId,seconds,System.currentTimeMillis()); refresh() }
        session?.cancel(); playback?.cancel(); timer?.cancel(); speechJob?.cancel(); aiJob?.cancel(); recognition.stop(); audio.stop()
        _ui.update { it.copy(active=false,playing=false,busy=false,generating=false,paused=false,waiting=false,preparing=false,nextInSeconds=0,status="학습 종료") }
    }
    fun rate(rating: Rating) {
        if(!ui.value.vehicle.parkedFeaturesAllowed) return
        val id=ui.value.lesson?.id ?: return
        viewModelScope.launch { repository.rate(id,rating,System.currentTimeMillis()); refresh(); _ui.update { it.copy(status="직접 평가 저장 · 다음 복습에 반영") } }
    }
    fun generate() {
        if(!ui.value.vehicle.parkedFeaturesAllowed || ui.value.busy) return
        stop()
        val s=ui.value
        val version=++generationVersion
        // Mark the request before launching: rapid taps cannot start overlapping jobs.
        _ui.update { it.copy(busy=true,generating=true,aiStatus="새 예문 생성 중…",status="Qwen이 이전 문장과 다른 예문을 만들고 있어요") }
        aiJob=viewModelScope.launch {
            try {
                val previous=(content.lessons(s.category,s.level).filter { it.id.startsWith("ai-") }.takeLast(5).map { it.english } + listOfNotNull(s.lesson?.english)).distinct().takeLast(6)
                val l=withTimeout(25_000) { ai.generate(s.level,s.category,s.lesson?.expression ?: "meeting",previous) }
                ensureActive()
                if(version!=generationVersion || !ui.value.vehicle.parkedFeaturesAllowed) return@launch
                require(previous.none { normalizeEnglish(it)==normalizeEnglish(l.english) }) { "이전 문장과 같은 예문이 반환되었습니다." }
                content.saveGenerated(l)
                _ui.update { it.copy(lesson=l,generatedCount=it.generatedCount+1,aiStatus="Qwen 새 예문 ${it.generatedCount+1}개 생성 완료",status="새 예문 생성 완료 · 다시 눌러 다른 문장을 만들 수 있어요") }
            } catch(e: TimeoutCancellationException) {
                if(version==generationVersion) _ui.update { it.copy(aiStatus="생성 시간 초과 · 다시 시도할 수 있어요",status="Qwen 응답이 늦습니다. 다른 예문 생성을 다시 눌러 주세요.") }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) {
                if(version==generationVersion) _ui.update { it.copy(aiStatus="새 예문 생성 실패 · 다시 시도 가능",status="새 예문을 받지 못했습니다. 현재 문장은 유지되며 다시 생성할 수 있어요.") }
            } finally {
                if(version==generationVersion) _ui.update { it.copy(busy=false,generating=false) }
            }
        }
    }
    private fun normalizeEnglish(text: String) = text.lowercase().replace(Regex("[^a-z0-9]+"), "")
    fun preview() {
        if(!ui.value.vehicle.parkedFeaturesAllowed || ui.value.active || ui.value.busy) return
        val l=ui.value.lesson ?: return
        speechJob?.cancel(); speechJob=viewModelScope.launch { try { speak(listOf(l.english to "en-US", l.korean to "ko-KR"),l.english) } catch(e: CancellationException) { throw e } catch(e: Exception) { _ui.update { it.copy(status=e.message ?: "음성 오류") } } }
    }
    fun startConversation() {
        if(!ui.value.vehicle.parkedFeaturesAllowed || ui.value.busy) return
        stop(); _ui.update { it.copy(conversation=emptyList(),turn=0) }; ask()
    }
    private fun ask() {
        aiJob=viewModelScope.launch {
            _ui.update { it.copy(busy=true) }
            try {
                val question=ai.question(ui.value.level,ui.value.turn)
                ensureActive(); if(!ui.value.vehicle.parkedFeaturesAllowed) return@launch
                _ui.update { it.copy(conversation=it.conversation+"AI: $question",turn=it.turn+1,aiStatus="Qwen 실제 회화") }
                speak(listOf(question to "en-US"),question)
            } catch(e: CancellationException) { throw e } catch(e: Exception) { _ui.update { it.copy(status="Qwen/음성 오류 · ${e.message}",aiStatus="회화 불가 · 연결 상태 확인") } }
            finally { _ui.update { it.copy(busy=false) } }
        }
    }
    fun answer(text: String, origin: String="텍스트 입력") {
        if(!ui.value.vehicle.parkedFeaturesAllowed || ui.value.busy || ui.value.turn !in 1..5 || text.isBlank() || ui.value.conversation.lastOrNull()?.startsWith("AI:") != true) return
        aiJob=viewModelScope.launch {
            _ui.update { it.copy(busy=true,conversation=it.conversation+"나 ($origin): ${text.take(500)}") }
            try {
                val f=ai.feedback(text,ui.value.explanationLanguage)
                ensureActive(); if(!ui.value.vehicle.parkedFeaturesAllowed) return@launch
                _ui.update { it.copy(conversation=it.conversation+"제안: ${f.corrected}\n${f.explanation}\n${if(f.uncertain) "판단이 불확실합니다. 점수를 매기지 않습니다." else "AI 제안입니다. 자동 채점하지 않습니다."}") }
                if(f.corrected.isNotBlank()) {
                    val lesson=Lesson("talk-${java.util.UUID.randomUUID()}",ui.value.category,ui.value.level,"conversation",f.corrected,if(ui.value.explanationLanguage=="ko") f.explanation else "회화 복습: ${f.explanation}","Qwen 회화 · 미검수")
                    content.saveGenerated(lesson); _ui.update { it.copy(lesson=lesson) }
                }
                speak(listOf(f.corrected to "en-US",f.explanation to if(ui.value.explanationLanguage=="ko") "ko-KR" else "en-US"),"AI 피드백")
                _ui.update { it.copy(status=if(it.turn>=5) "5턴 회화 완료" else "다음 질문으로 계속할 수 있습니다.") }
            } catch(e: CancellationException) { throw e } catch(e: Exception) { _ui.update { it.copy(status="피드백 실패 · ${e.message}") } }
            finally { _ui.update { it.copy(busy=false) } }
        }
    }
    fun nextQuestion() { if(ui.value.vehicle.parkedFeaturesAllowed && !ui.value.busy && ui.value.turn in 1..4 && ui.value.conversation.lastOrNull()?.startsWith("제안:")==true) ask() }
    fun listen(command: Boolean) {
        if(!command && !ui.value.vehicle.parkedFeaturesAllowed) return
        if(ui.value.active) pause() else audio.pause()
        _ui.update { it.copy(status="마이크 듣는 중 · ${if(command) "반복 / 다음 / 종료 / 일시정지 / 재개" else "영어로 답해 주세요"}") }
        recognition.listen(if(command) "ko-KR" else "en-US",{ text ->
            if(command) when(parseCommand(text)) { VoiceCommand.REPEAT -> repeat(); VoiceCommand.NEXT -> next(); VoiceCommand.STOP -> stop(); VoiceCommand.PAUSE -> pause(); VoiceCommand.RESUME -> resume(); else -> _ui.update { it.copy(status="지원하지 않는 명령: $text · 재개할 수 있습니다.") } }
            else answer(text,"실제 Android STT")
        },{ message -> _ui.update { it.copy(status=message) } })
    }
    fun clear() { if(!ui.value.vehicle.parkedFeaturesAllowed) return; stop(); viewModelScope.launch { repository.clear(); refresh(); getApplication<Application>().cacheDir.listFiles()?.filter { it.name.startsWith("speech-") }?.forEach { it.delete() }; _ui.update { it.copy(conversation=emptyList(),turn=0,status="로컬 학습 기록 삭제 완료") } } }
    fun hidden() { if(!ui.value.active) stop() }
    override fun onCleared() { stop(); recognition.close(); speech.close(); audio.close(); db.close(); super.onCleared() }
}
