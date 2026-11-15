package ai.drivetalk.ui
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.drivetalk.learning.Rating
import ai.drivetalk.vehicle.VehicleState

private val Ink=Color(0xFF0B121B)
private val Panel=Color(0xFF142131)
private val Mint=Color(0xFF9FF4D1)
private val Muted=Color(0xFFA6B6C8)
private val Scheme=darkColorScheme(primary=Mint,onPrimary=Ink,background=Ink,surface=Panel,onSurface=Color(0xFFEEF4FA),secondary=Color(0xFFA6CBFF))
@Composable fun DriveApp(model: DriveViewModel, microphone: (Boolean)->Unit) {
    val s by model.ui.collectAsStateWithLifecycle()
    MaterialTheme(colorScheme=Scheme) {
        Surface(Modifier.fillMaxSize(),color=Ink) {
            BoxWithConstraints(Modifier.safeDrawingPadding()) {
                val wide=maxWidth>850.dp
                Column(Modifier.fillMaxSize().padding(if(wide) 28.dp else 18.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        Column { Text("DRIVE TALK AI",color=Mint,fontSize=24.sp,fontWeight=FontWeight.ExtraBold); Text("작은 이동, 더 넓은 영어",color=Muted,fontSize=12.sp) }
                        Column(horizontalAlignment=Alignment.End) {
                            Text(if(s.vehicle.demo) "DEMO · ${s.vehicle.state.name}" else "차량 상태 미확인",color=if(s.vehicle.demo) Color(0xFFFFD28B) else Muted,fontWeight=FontWeight.Bold,fontSize=13.sp)
                            Text("Android 대체 음성 · Pleos 미연동",color=Muted,fontSize=10.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("home" to "오늘의 학습","talk" to "AI 영어회화","history" to "학습 기록","settings" to "설정").forEach { (key,label) ->
                            FilterChip(selected=s.tab==key,onClick={model.tab(key)},label={Text(label)},enabled=key !in listOf("talk","history") || s.vehicle.parkedFeaturesAllowed)
                        }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        when(s.tab) {
                            "home" -> {
                                Text(if(s.active) "LISTEN & GROW" else "YOUR DAILY DRIVE",color=Mint,fontSize=12.sp,letterSpacing=2.sp)
                                Text(if(s.active) "듣기에 집중하세요." else "출퇴근 시간을\n나만의 영어 시간으로.",fontSize=if(wide) 36.sp else 29.sp,fontWeight=FontWeight.Bold,lineHeight=40.sp)
                                Text("${categoryName(s.category)}  /  ${levelName(s.level)}  /  ${s.lessonCount}개 문장",color=Muted)
                                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { Action("출근길 학습 시작",{model.start()},!s.busy,Modifier.weight(1f)); Action("퇴근길 복습",{model.start(true)},!s.busy,Modifier.weight(1f),false) }
                                CardBox {
                                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text(if(s.paused) "Ⅱ  일시정지" else if(s.waiting) "따라 말할 시간" else if(s.playing) "●  재생 중" else if(s.active) "음성 준비 중" else "오늘의 영어",color=Mint,fontWeight=FontWeight.Bold); if(s.active) Text("${s.currentNumber} / ${s.sessionTotal}개 · 청취 ${s.completedCount}개",color=Mint) }
                                    if(s.vehicle.parkedFeaturesAllowed) {
                                        Text(s.lesson?.english ?: "학습을 시작해 보세요.",fontSize=if(wide) 28.sp else 23.sp,lineHeight=33.sp,fontWeight=FontWeight.Medium)
                                        Text(s.lesson?.korean ?: "",color=Muted,fontSize=16.sp)
                                    } else { Text("영어 → 한국어 → 영어 반복",fontSize=24.sp); Text("화면 조작을 줄이고 오디오에 집중하세요.",color=Muted) }
                                    Text(s.lesson?.source ?: "기본 콘텐츠",fontSize=11.sp,color=Muted)
                                    if(s.active) {
                                        if(s.waiting) Text(if(s.paused) "쉬는 시간도 일시정지했어요." else "${s.nextInSeconds}초 뒤 다음 문장 · 반복을 눌러 더 연습할 수 있어요.",color=Muted)
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Action("반복",{model.repeat()},modifier=Modifier.weight(1f),primary=false); Action(if(s.paused) "재개" else "일시정지",{if(s.paused) model.resume() else model.pause()},modifier=Modifier.weight(1f)); Action("다음",{model.next()},modifier=Modifier.weight(1f),primary=false) }
                                        Action("음성 명령",{microphone(true)},modifier=Modifier.fillMaxWidth(),primary=false)
                                    } else if(s.vehicle.parkedFeaturesAllowed) Action("문장 들어보기",{model.preview()},!s.busy,Modifier.fillMaxWidth())
                                }
                                if(s.vehicle.parkedFeaturesAllowed) {
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Action("다시 학습",{model.rate(Rating.AGAIN)},modifier=Modifier.weight(1f),primary=false); Action("어려워요",{model.rate(Rating.HARD)},modifier=Modifier.weight(1f),primary=false); Action("알겠어요",{model.rate(Rating.GOOD)},modifier=Modifier.weight(1f),primary=false) }
                                    CardBox { Text("내 수준에 맞춘 새 문장",fontSize=18.sp,fontWeight=FontWeight.Bold); Text(s.aiStatus,color=Muted,fontSize=13.sp); Action(if(s.generating) "새 예문 생성 중…" else if(s.generatedCount>0) "다른 예문 생성" else "Qwen 예문 생성",{model.generate()},!s.busy,Modifier.fillMaxWidth(),false) }
                                } else Text("회화·평가·상세 기록은 주차 확인 후 사용할 수 있습니다. 시연은 설정에서 DEMO 주차를 선택하세요.",color=Muted,fontSize=13.sp)
                            }
                            "talk" -> {
                                Text("PARK & SPEAK",color=Mint,letterSpacing=2.sp)
                                Text("편안하게, 영어 한마디.",fontSize=30.sp,fontWeight=FontWeight.Bold)
                                Text("최대 5턴 · 로컬 Qwen · 자동 채점 없음",color=Muted)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Action("새 회화 시작",{model.startConversation()},!s.busy,Modifier.weight(1f)); Action("다음 질문",{model.nextQuestion()},!s.busy && s.turn in 1..4,Modifier.weight(1f),false) }
                                s.conversation.forEach { line -> CardBox { Text(line,fontSize=18.sp,lineHeight=27.sp) } }
                                var answer by remember { mutableStateOf("") }
                                OutlinedTextField(answer,{answer=it.take(500)},label={Text("주차 중 텍스트 답변 (음성 입력과 구분)")},modifier=Modifier.fillMaxWidth(),enabled=!s.busy)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Action("답변 보내기",{model.answer(answer);answer=""},!s.busy && answer.isNotBlank(),Modifier.weight(1f)); Action("마이크로 답변",{microphone(false)},!s.busy && s.turn in 1..5,Modifier.weight(1f),false) }
                                Action("현재 교정 표현을 어려움으로 저장",{model.rate(Rating.HARD)},!s.busy && s.lesson?.id?.startsWith("talk-")==true,Modifier.fillMaxWidth(),false)
                            }
                            "history" -> {
                                Text("MY PROGRESS",color=Mint,letterSpacing=2.sp); Text("꾸준함이 실력이 되도록.",fontSize=30.sp,fontWeight=FontWeight.Bold)
                                CardBox { Text("${s.progress.sumOf { it.listens }}회 청취     ${s.progress.count { it.difficult }}개 어려운 표현     ${s.learnedSeconds/60}분 ${s.learnedSeconds%60}초",fontSize=22.sp); Text("청취는 정답으로 처리하지 않습니다. 직접 평가만 SRS에 반영됩니다.",color=Muted) }
                                if(s.progress.isEmpty()) Text("아직 기록이 없습니다. 첫 학습을 시작해 보세요.",color=Muted)
                                s.progress.sortedWith(compareByDescending<ai.drivetalk.learning.Progress> { it.difficult }.thenBy { it.due }).forEach { p -> CardBox { Text(p.lessonId,fontWeight=FontWeight.Bold); Text("청취 ${p.listens} · 정답 ${p.correct} · 오답 ${p.wrong} · ${if(p.difficult) "어려움" else "일반"}",color=Muted); Text(if(p.due>0) "다음 복습: ${java.text.SimpleDateFormat("MM/dd HH:mm",java.util.Locale.KOREA).format(java.util.Date(p.due))}" else "평가 전 · 복습 일정 미정",color=Muted,fontSize=12.sp) } }
                            }
                            "settings" -> {
                                Text("MAKE IT YOURS",color=Mint,letterSpacing=2.sp); Text("나에게 맞는 드라이브.",fontSize=30.sp,fontWeight=FontWeight.Bold)
                                CardBox {
                                    Text("차량 연결 · DEMO",fontSize=20.sp,fontWeight=FontWeight.Bold)
                                    Text("실제 Pleos 차량 상태에 연결되지 않았습니다. DEMO 전환은 시연용이며 재실행하면 미확인 상태로 돌아갑니다.",color=Muted)
                                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Action("DEMO 주차",{model.simulate(VehicleState.PARKED)},modifier=Modifier.weight(1f),primary=false); Action("DEMO 주행",{model.simulate(VehicleState.DRIVING)},modifier=Modifier.weight(1f),primary=false); Action("미확인",{model.disableDemo()},modifier=Modifier.weight(1f),primary=false) }
                                }
                                if(s.vehicle.parkedFeaturesAllowed) {
                                    CardBox {
                                        Text("영어 수준",fontWeight=FontWeight.Bold)
                                        Choices(listOf("beginner","intermediate","advanced"),s.level,{levelName(it)}) { model.settings(level=it) }
                                        Text("학습 카테고리",fontWeight=FontWeight.Bold)
                                        Choices(listOf("business","daily","travel"),s.category,{categoryName(it)}) { model.settings(category=it) }
                                        Text("학습할 문장 개수",fontWeight=FontWeight.Bold)
                                        Choices(listOf("3","5","10","15"),s.lessonCount.toString(),{"${it}개"}) { model.settings(lessonCount=it.toInt()) }
                                        Text("영어 → 2초 쉬기 → 한국어 → 2초 쉬기 → 영어 반복. 준비된 문장이 적으면 해당 개수만 학습합니다.",color=Muted,fontSize=13.sp)
                                        Text("다음 문장까지 쉬는 시간",fontWeight=FontWeight.Bold)
                                        Choices(listOf("5","8","12"),s.gapSeconds.toString(),{"${it}초"}) { model.settings(gapSeconds=it.toInt()) }
                                        Text("회화 설명 언어",fontWeight=FontWeight.Bold)
                                        Choices(listOf("ko","en"),s.explanationLanguage,{if(it=="ko") "한국어" else "English"}) { model.settings(language=it) }
                                    }
                                    CardBox {
                                        Text("출퇴근 준비 시간",fontWeight=FontWeight.Bold)
                                        Text("사용자가 선택한 모드로만 학습합니다. 시간만으로 출퇴근을 판단하거나 자동 재생하지 않습니다.",color=Muted)
                                        Text("출근 ${s.morningHour}시 / 퇴근 ${s.eveningHour}시")
                                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Action("출근 시간 +1",{model.settings(morning=(s.morningHour+1)%24)},modifier=Modifier.weight(1f),primary=false); Action("퇴근 시간 +1",{model.settings(evening=(s.eveningHour+1)%24)},modifier=Modifier.weight(1f),primary=false) }
                                    }
                                    CardBox { Text("데이터와 연결",fontWeight=FontWeight.Bold); Text("학습 기록은 이 기기의 Room DB에 보관됩니다. 로컬 Qwen에는 수준·주제·답변 텍스트만 보냅니다. Android STT는 기기 설정에 따라 외부 서비스를 사용할 수 있습니다.",color=Muted); Text("AI: 개발 PC 10.0.2.2:8765\n음성: Android TTS / Android STT",color=Muted,fontSize=12.sp); var confirm by remember { mutableStateOf(false) }; Action(if(confirm) "한 번 더 눌러 전체 기록 삭제" else "로컬 학습 기록 삭제",{if(confirm){model.clear();confirm=false}else confirm=true},modifier=Modifier.fillMaxWidth(),primary=false) }
                                }
                            }
                        }
                    }
                    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(s.status,color=Muted,fontSize=12.sp,maxLines=2); Action("■  학습 종료",{model.stop()},modifier=Modifier.fillMaxWidth(),primary=false) }
                }
            }
        }
    }
}
@Composable private fun CardBox(content: @Composable ColumnScope.()->Unit) { Column(Modifier.fillMaxWidth().background(Panel,RoundedCornerShape(20.dp)).padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp),content=content) }
@Composable private fun Action(label: String, click: ()->Unit, enabled: Boolean=true, modifier: Modifier=Modifier, primary: Boolean=true) {
    if(primary) Button(onClick=click,enabled=enabled,modifier=modifier.heightIn(min=56.dp),shape=RoundedCornerShape(13.dp),contentPadding=PaddingValues(horizontal=14.dp,vertical=14.dp)) { Text(label,fontWeight=FontWeight.Bold) }
    else OutlinedButton(onClick=click,enabled=enabled,modifier=modifier.heightIn(min=56.dp),shape=RoundedCornerShape(13.dp),contentPadding=PaddingValues(horizontal=12.dp,vertical=14.dp)) { Text(label) }
}
@Composable private fun Choices(items: List<String>, current: String, label: (String)->String, select: (String)->Unit) { Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { items.forEach { item -> FilterChip(selected=current==item,onClick={select(item)},label={Text(label(item))}) } } }
fun categoryName(value: String)=when(value){"business"->"비즈니스 영어";"daily"->"일상회화";else->"여행영어"}
fun levelName(value: String)=when(value){"beginner"->"초급";"intermediate"->"중급";else->"고급"}
