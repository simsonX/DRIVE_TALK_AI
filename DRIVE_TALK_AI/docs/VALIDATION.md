# 실제 검증 결과

검증일: 2026-11-15, Asia/Seoul. 일반 Android DEMO MVP이며 실제 차량 인증 결과가 아니다.

## 빌드와 자동 테스트

| 검증 | 결과 |
|---|---|
| Android debug APK 빌드 | 성공, `app/build/outputs/apk/debug/app-debug.apk` |
| Kotlin 단위 테스트 | 8개 통과: SRS/명령/빈 복습/안전 상태 |
| Android 기기 테스트 | 4개 통과: Room 저장·재열기·삭제, 실제 영/한 TTS, Media3 재생/포커스, 주행 회화 차단 |
| Python 서버 테스트 | 11개 통과: 구조/언어/실패/제한된 재시도 |
| 예문 자산 검사 | 54개, 고유 ID, 9그룹, 기본 문장부호/한국어 검사 통과 |
| Android lint | 오류 0. 버전 업데이트 권고·KAPT 등 경고 존재 |
| Pleos 별도 라이브러리 Kotlin 컴파일 | 실제 STT/TTS 3.3.1, Vehicle 2.3.2에 대해 성공 |

Android 통합 명령:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:lintDebug :pleos-adapters:assembleDebug
```

실행 보고서:

- `app/build/reports/tests/testDebugUnitTest/index.html`
- `app/build/reports/androidTests/connected/debug/index.html`
- `app/build/reports/lint-results-debug.html`
- `docs/evidence/`의 최종 실행 기록과 화면

## 확인한 동작과 한계

| 항목 | 실제 확인 범위 |
|---|---|
| 영어·한국어 음성 | Android TTS가 실제 WAV를 생성하고 Media3로 재생 완료. 물리적 차량 스피커 검증 아님 |
| 반복/다음/종료 | 통합 테스트에서 컨트롤 후 정지 상태 확인 |
| 오디오 포커스 | Android AudioManager로 내비 MAY_DUCK 요청, 음성 일시정지와 포커스 반환 후 복귀 통과 |
| 전화 | 포커스 처리 구현. 실제 통신망 전화 수신은 미검증 |
| 학습 기록 | 실제 Room 파일 재열기와 삭제 통과. 청취만으로 정답/오답 변화 없음 |
| 개인화 | 어려움 우선 정렬 및 SRS 간격 단위 테스트. 직접 평가 기록이 실제 DB에 반영 |
| 차량 상태 실패 | UNKNOWN에서 주차 기능 차단, DEMO 주행 전환에서 회화 진입 거부 |
| 음성 인식 | 실제 Android SpeechRecognizer 구현. 실제 사람이 말한 발화의 인식 정확도는 미검증 |
| STT 오류 | 서비스 미지원/권한 거부/인식 오류 안내 코드. 모든 기기별 오류의 자동 주입 테스트는 미완료 |
| AI 서버 실패 | 서버 오프라인과 모델 잘못된 JSON을 Python 테스트에서 검증. 앱 기본 콘텐츠 복귀 구현 |
| 인터넷 단절 | 기본 예문 로컬 저장. 에뮬레이터의 실제 전체 네트워크 단절 시나리오는 별도 확인 필요 |
| 실제 Pleos 음성·차량 | SDK 컴파일 검증만 완료. 앱은 Android 대체 음성과 DEMO/UNKNOWN 사용 |
| 에뮬레이터 | 일반 Android 17 ARM64, 1600×900 시연 override. Pleos 에뮬레이터 아님 |

## 모델 검증

최초 Qwen2.5 1.5B를 실제 다운로드·구동했다. 두 수준의 다른 예문과 질문 생성은 성공했다. 한국어 설명을 영어로 반환하거나 교정 문장을 한국어로 반환하는 실패가 관측되어 서버가 거부했다. JSON 문법이 맞아도 언어와 내용 검증이 필요함을 확인했다.

최종 기본 모델은 **Qwen3 4B (Q4_K_M, Ollama, think=false)**다. 같은 시나리오의 예문 2개·질문·한국어 교정 피드백이 모두 실제 추론으로 성공했다. 1.5B 실패 표본은 `qwen2.5-1.5b-evidence.json`에 보관한다.

최종 모델의 실제 요청·응답·소요 시간은 `qwen-evidence.json`에 보관한다. 기본 콘텐츠나 고정 응답을 Qwen 추론 결과로 사용하지 않는다. 모델이 산출한 언어 설명을 완벽한 문법 판정이라고 주장하지 않는다.

## 성능

`playback-evidence.json`은 실제 청취 시작 요청→Media3 재생 시작 지연과 포커스 검증 결과다. `evidence/cold-start.txt`는 adb ActivityManager 실행 시간, `evidence/meminfo.txt`는 단일 시점 프로세스 PSS다. 이 수치는 개발 PC의 일반 에뮬레이터 표본으로, 차량 성능·평균·최악값·장시간 안정성을 뜻하지 않는다.

Qwen 응답 시간은 요청별 실제 측정값을 `qwen-evidence.json`에 기록한다. 첫 로딩과 워밍 후 지연은 다를 수 있다. 모델이 개발 PC에서 동작하며 차량 메모리/추론 시간을 측정하지 않았다.

## 남은 기술적 제약

- 실제 차량 상태·CarUxRestrictions와 앱 연결 미완료. 실차에서는 UNKNOWN 차단 상태 유지.
- Pleos 음성 브리지는 데모 앱에 활성화하지 않음. 초기화·언어 지원·서비스 수명·포커스를 실제 Pleos에서 검증해야 함.
- MediaSessionService까지만 구현. 차량용 MediaLibraryService 탐색/추천 트리와 스토어 UX 심사는 후속 작업.
- STT는 버튼으로 시작. Gleo/웨이크워드/스티어링 휠 연동이 없어 완전한 화면 무조작 음성 진입을 제공하지 못함.
- Qwen 출력은 구조·언어 규칙을 검증하지만 의미·번역·문법의 완전성은 보장하지 못함. 생성 콘텐츠 검수 강화 필요.
- 단일 사용자 `local`, 현재 카테고리/수준의 복습. 사용자 계정·차량 프로필 및 수준 간 통합 복습 없음.
- 출퇴근 시간은 저장만 수행. 백그라운드 콘텐츠 자동 준비는 후속 작업. 시간 기반 자동 재생하지 않음.
- 로컬 HTTP endpoint는 개발 에뮬레이터용. production TLS/서버 인증·배포 설정은 미구현.
- 릴리스 APK/배포 서명/실차 인증 미수행. 전달 APK는 debug APK다.

### 측정 표본

| 항목 | 결과 |
|---|---|
| 앱 cold start (adb TotalTime) | 941ms |
| 기본 학습 시작→Media3 playing | 1,944ms |
| 초기 화면 앱 TOTAL PSS | 79,554KiB (약 77.7MiB) |
| Qwen3 첫 예문 요청 (모델 로딩 포함) | 11,381ms |
| Qwen3 중급 예문 (워밍 후) | 1,490ms |
| Qwen3 회화 질문 | 780ms |
| Qwen3 한국어 교정 피드백 | 2,346ms |
| APK | 약 35MiB, debug |

단일 표본이며 통계적 벤치마크가 아니다. Android lint는 오류 0/경고 16이다. 경고는 주로 의존성/target SDK 업데이트 권고, KAPT, 스타일, 외부 미디어 컨트롤을 위한 exported service 관련이다.

## 실제 앱 수동 통합 확인

- Android 앱에서 Qwen 질문을 생성하고 Android TTS로 출력했다.
- `I go to work yesterday.`를 **텍스트 입력**으로 제출했다. 실제 Qwen이 `I went to work yesterday.`와 한국어 시제 설명을 반환했고 영어/한국어 음성이 끝까지 재생되었다. `evidence/qwen-conversation.png`.
- 턴별 주제 명시 후 실제 Qwen이 3개 턴의 서로 다른 질문을 생성했다. `three-turn-questions.json`. 이 검증은 3회 질문 API 검증이며 사람과의 3턴 마이크 대화 검증은 아니다.
- 실제 앱에서 Qwen 새 예문을 생성·Room 저장·미리 듣기했다. `evidence/qwen-generated.png`.
- 생성 문장을 직접 HARD 평가한 뒤 퇴근 복습을 시작하여 같은 문장이 첫 번째로 선택되는 것을 확인했다. `evidence/srs-ui-verification.txt`, `evidence/evening-review.png`.
- 기록 화면에 청취 4회, 어려운 표현 1개, 정답/오답 0, 세션 시간 34초가 보존되었다. `evidence/learning-history.png`. 테스트와 시연용 단일 로컬 사용자의 기록이다.
- Qwen3 실행 메모리 보고값은 약 3.19GB(2.97GiB). `evidence/ollama-model-memory.json`. 이 파일에는 비교 당시 함께 로드했던 1.5B 모델도 포함된다. 비교가 끝난 후 1.5B는 메모리에서 해제했다.
- 최종 APK SHA-256: `evidence/apk-sha256.txt`. 최종 설치본에 대해 별도 instrumentation 실행도 `OK (4 tests)`로 끝났다. `evidence/device-tests.txt`.

## 개수 세션 수정 검증

시간 제한을 없애고 3·5·10·15개 문장 선택, 언어 사이 2초 쉼, 문장 사이 5·8·12초 대기(기본 8초), TTS 속도 0.78로 변경했다. 새 설정은 `lessonCount`/`gapSeconds` 키를 사용하며 기존 분 설정은 가져오지 않는다.

- Kotlin 테스트 **13개 통과**: 기존 8개 + 유한 개수 종료/반복 중복 방지/건너뛰기/준비 콘텐츠 상한/PCM 쉼 검증 5개.
- 에뮬레이터 테스트 **5개 통과**: 기존 4개 + 실제 문장 재생 후 대기 진입, 일시정지 중 카운트다운 정지, 재개 후 다음 문장, 건너뛴 항목의 청취 제외와 세션 종료 확인.
- APK 재빌드·재설치·실행 완료. 이 절의 결과가 초기 분 단위 시연 기록보다 최신이다.

## Qwen 재생성 수정 검증

- 같은 조건의 실제 요청 세 번에서 1회차·3회차 동일 응답을 재현했다. 최근 예문 6개를 제외하는 요청/검증과 새 생성 회차 표시를 추가했다.
- Python **14개 통과**: 최근 문장 요청 제한, 대소문자/문장부호를 제외한 중복 거부, 중복 후 새 문장 재시도 포함. Kotlin 단위 **13개 통과**.
- Android **10개 통과** (`liveAi=true`): 기존 개수/재생/Room/안전/음성 5개 + 재생성 5개(연속 생성 후 버튼 상태 복구, 실패 후 재시도, 중복 유지/거부, 취소 작업의 상태 경쟁, 실제 Qwen 세 번 연속 서로 다른 결과).
- 테스트용 고정 응답은 `TEST_ONLY` 출처의 테스트 APK에만 사용했으며, 실제 Qwen 검사에서는 로컬 Qwen3 4B를 호출했다. 두 검사를 구분했다.
- APK 업데이트 직후 System UI의 이전 Activity 복원과 첫 테스트가 겹친 실패 1회가 있었다. 앱을 force-stop한 후 재실행한 최종 전체 결과는 `OK (10 tests)`다.
- 최종 로그: `evidence/qwen-regeneration-device-tests.txt`, 실제 생성 문장: `evidence/qwen-regeneration-live.txt`.
