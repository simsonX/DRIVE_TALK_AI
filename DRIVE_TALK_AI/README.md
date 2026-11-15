# DRIVE TALK AI

출퇴근에 맞춘 오디오 영어 학습 Android MVP. 별도 프로젝트로 구현했으며 YEOPJARI를 수정하지 않았다.

**실행 범위:** 일반 Android 에뮬레이터의 DEMO. Pleos SDK 어댑터는 별도 모듈에서 컴파일 검증하며, 실제 Pleos 차량/에뮬레이터 런타임 연결은 완료하지 않았다. 화면의 DEMO·대체 음성 표시를 확인한다.

## 기능

- 비즈니스·일상·여행 / 초급·중급·고급 자체 작성 기본 문장 54개.
- 영어 → 한국어 → 영어 반복, 3·5·10·15개 문장 세션, 느린 음성과 문장 사이 여유 시간.
- Media3 미디어 세션, 재생/일시정지/재개/다음/반복/종료, Android 실제 TTS.
- Android STT를 통한 제한된 음성 명령 및 주차 회화 입력. 텍스트 입력과 출처 구분.
- Room 청취·평가·어려움·복습 예정·세션 시간 기록, 로컬 삭제.
- 직접 평가 기반 SRS. 청취만으로 정답을 추가하지 않음.
- 출근 새 표현과 퇴근 복습을 사용자가 직접 선택. 설정 시간으로 임의 자동 재생하지 않음.
- 로컬 Qwen 예문·번역·회화 질문·교정 제안, JSON 검증과 기본 콘텐츠 대체.
- 주차 DEMO에서 최대 5턴 회화. 자동 채점 없음.
- UNKNOWN/DRIVING에서는 회화·평가·상세 기록 차단.

## Android 실행

Android Studio에서 **이 README가 있는 DRIVE_TALK_AI 폴더**를 연다. JDK 21(또는 AGP 호환 JDK), Android SDK API 36, 일반 Android API 28+ 기기가 필요하다. SDK 경로는 각 PC의 `local.properties`에 설정한다(커밋 제외).

```sh
printf 'sdk.dir=%s\n' "$HOME/Library/Android/sdk" > local.properties
./gradlew :app:assembleDebug :app:testDebugUnitTest
"$HOME/Library/Android/sdk/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$HOME/Library/Android/sdk/platform-tools/adb" shell am start -n ai.drivetalk/.MainActivity
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. debug 서명이며 스토어 배포/실차 인증용 릴리스가 아니다. API 36, Room, Compose, Media3 의존성을 처음 빌드할 때 인터넷이 필요하다.

Gradle wrapper는 8.14.3을 사용한다. 실제 검증 때 설치된 동일 버전 Gradle도 사용했다. Android Studio의 Sync 또는 `./gradlew`로 재현할 수 있다.

## 로컬 AI 실행

유료 API/키가 필요 없다. Ollama를 설치하고 아래 무료 로컬 모델을 내려받는다. 파인튜닝이나 LoRA를 수행하지 않는다.

```sh
ollama serve
# 별도 터미널:
ollama pull qwen3:4b
python3 ai-server/server.py
```

브리지는 `127.0.0.1:8765`, Ollama는 `127.0.0.1:11434`를 사용한다. 별도 Python 패키지 없이 Python 3 표준 라이브러리만 필요하다.

모델 교체:

```sh
ollama pull qwen2.5:1.5b
QWEN_MODEL=qwen2.5:1.5b python3 ai-server/server.py
```

최종 실제 선택 모델과 측정 결과는 `docs/VALIDATION.md`와 `docs/qwen-evidence.json`을 확인한다. 작은 모델은 번역·설명의 정확성이 제한적이다. 서버가 연결되지 않아도 검수 기본 문장 학습은 가능하며, 회화는 연결 오류를 표시한다.

```sh
curl http://127.0.0.1:8765/health
python3 scripts/verify_qwen.py
```

`health`는 모델 설치 상태 확인이다. 실제 추론 성공 여부는 통합 검증 결과로 확인한다.

앱 endpoint는 개발 에뮬레이터의 호스트 주소 `http://10.0.2.2:8765`다. 로컬 HTTP는 debug manifest에서만 허용한다. release 및 실제 기기 배포는 endpoint/TLS 설정을 별도로 구성해야 한다. 개발 브리지를 외부 인터넷에 공개하지 않는다.

## 에뮬레이터

설치된 AVD 목록과 실행:

```sh
"$HOME/Library/Android/sdk/emulator/emulator" -list-avds
"$HOME/Library/Android/sdk/emulator/emulator" -avd Medium_Phone_API_37.0
```

차량 화면 시연용 일반 Android 화면 설정(실제 Pleos 해상도 아님):

```sh
adb shell wm size 1600x900
adb shell wm density 160
# 원래 화면으로 복원:
adb shell wm size reset
adb shell wm density reset
```

이 PC에서는 `adb`가 PATH에 없어서 위 Android SDK `platform-tools/adb` 전체 경로를 사용했다. 영어·한국어 Android TTS 음성 데이터가 있어야 한다. 음성 인식은 마이크 권한과 실제 STT 서비스가 필요하고 기기/네트워크 설정에 의존한다.

Pleos 에뮬레이터는 현재 설치되어 있지 않다. 설치는 공식 Pleos 개발자 문서를 따른다. SDK AAR만으로 차량 서비스가 제공되지는 않는다.

## 테스트

```sh
python3 scripts/check_content.py
python3 -m unittest discover -s ai-server -v
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
./gradlew :pleos-adapters:assembleDebug
```

- 순수 Kotlin: SRS 간격/상한, 순위, 빈 복습, 명령 파싱, UNKNOWN/DRIVING 차단.
- 기기: Room 저장/재열기/삭제, 영어·한국어 TTS, 실제 Media3 재생·포커스, 주행 전환 게이트.
- Python: 요청/응답 스키마, 잘못된 언어/JSON, 오프라인 재시도, 개인정보 과다 필드 거부.
- 별도 로컬 모델 통합 검사: 실제 두 수준 예문, 회화 질문, 교정 제안 및 응답 시간.

실행 성공/실패/미검증을 구분한 결과는 `docs/VALIDATION.md`에 기록한다.

## 문서

- [시연 순서와 대체 방법](docs/DEMO.md)
- [구조·데이터 흐름·SRS·AI/음성 계약](docs/ARCHITECTURE.md)
- [환경 조사·YEOPJARI 분석·Pleos 경계](docs/ENVIRONMENT_AND_PLEOS.md)
- [실행 검증·성능·제약](docs/VALIDATION.md)
- [개발 기록](docs/CHANGELOG.md)

현재 버전은 단일 로컬 사용자, 일반 Android 데모다. 실제 차량 안전 인증, Pleos 스토어 심사, Gleo 호출/스티어링 휠 음성 트리거, 실차 전화/내비 검증, 온디바이스 차량 Qwen은 후속 개발 범위다. 노래방·팝송·음악 생성 기능은 포함하지 않는다.
