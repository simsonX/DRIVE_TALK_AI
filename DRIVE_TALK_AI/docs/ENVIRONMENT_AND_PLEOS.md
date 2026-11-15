# 조사 환경 및 Pleos 연동 경계

조사일: 2026-11-15 (Asia/Seoul).

| 항목 | 확인 결과 |
|---|---|
| 작업 폴더 | `/Users/user/Documents/ChatGPT/현대차` |
| 기존 Git | 파일/커밋 없는 main. 별도 `codex/drive-talk-ai-mvp` 브랜치 생성 |
| 새 프로젝트 | `DRIVE_TALK_AI/`, YEOPJARI와 별도 패키지/소스 |
| 개발 PC | Apple Silicon arm64, 메모리 16GiB |
| Android Studio | `/Applications/Android Studio.app` 설치 |
| Java | JBR OpenJDK 21.0.11, 앱 JVM target 17 |
| Gradle/AGP/Kotlin | 8.14.3 / 8.13.2 / 2.3.21 |
| Android SDK | `~/Library/Android/sdk`, API 36 및 37.0 설치 |
| AVD | `Medium_Phone_API_37.0`, Google Play ARM64 일반 Android 17 |
| 시연 화면 | 설치된 AVD 화면을 1600×900, density 160으로 override. 실제 Pleos 대상 해상도 아님 |
| Pleos AVD/서비스 | 설치된 시스템 이미지·AVD·실행 패키지에서 확인되지 않음 |
| 실제 Pleos SDK | Gradle 캐시: Vehicle 2.3.2, STT/TTS 3.3.1, NaviHelper 3.0.5 등 |
| 음성 대체 엔진 | Android Google TTS, Android SpeechRecognizer |
| 로컬 AI | 설치된 Ollama, 외부 유료 API 없음 |

## YEOPJARI 분석

참고 저장소: https://github.com/blockchain77/yeopjari

임시 읽기 복사본 `/private/tmp/drive-talk-reference`에서 구조를 조사했다. 원본에 변경을 가하지 않았다. 프로젝트 코드/리소스를 복사하지 않고 다음 설계 패턴만 참고했다.

- `core/gateway` 계층으로 플랫폼 의존성을 숨기는 구조.
- `platform/pleos/PleosSpeech.kt`: SDK `ON_DEVICE`, 초기화 완료 콜백, 언어 설정, listener 해제, Android 대체 TTS.
- `platform/pleos/PleosVehicleGateway.kt`: 표준 AAOS Car API와 Pleos API의 역할 구분, 비동기 IPC, 지원/실패 처리.
- `settings.gradle.kts`: Pleos 공식 Maven 저장소.
- Kotlin/Compose 빌드 구성과 SDK 버전.

DRIVE TALK AI 별도 구현: 학습 예문, SRS, Room 스키마, 재생 큐, 출퇴근 세션, Qwen 브리지/프롬프트, 대화·안전 UI, 테스트. YEOPJARI의 동승자/차량 제어/음악 기능은 포함하지 않는다.

## 실제 SDK 검사

캐시의 AAR에서 `classes.jar`를 추출하여 `javap`로 TTS 생성자·initialize·listener·setLocale·speak·stop·release 시그니처를 직접 확인했다. 공식 문서와 실제 컴파일러가 허용하는 메서드만 브리지에 사용했다.

`pleos-adapters`는 실제 SDK에 대해 Kotlin 컴파일하는 별도 라이브러리다. 데모 앱의 dependency에 포함하지 않는다. SDK 파일이 있다는 것과 Pleos Speech/Vehicle 서비스가 실행된다는 것은 다르다.

**결과: SDK 수준 컴파일 검증까지 수행. Pleos 음성·차량 런타임 통합 완료는 아님.**

Pleos TTS는 차량 서비스가 직접 스트리밍 재생한다. Android TTS의 `synthesizeToFile`과 동일한 메서드가 있다고 가정하지 않았다. 현재 Media3 WAV 경로와 연결하려면 별도 스트리밍 출력/오디오 포커스 어댑터 및 실차 테스트가 필요하다.

Vehicle 2.3.2의 기어 조회 메서드는 실제 컴파일 시 deprecated 경고를 내며 Google `GEAR_SELECTION` 속성 사용을 안내한다. 참고 브리지의 조회만으로 주차 UI를 열지 않는다. 향후 `CarPropertyManager`의 gear/speed/parking brake와 `CarUxRestrictionsManager`를 결합하고, 서비스 단절/오류/오래된 값은 UNKNOWN으로 처리해야 한다. 문서에 없는 상태 API를 만들지 않았다.

## 공식 조사 자료

- Pleos SDK 개요: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/
- Pleos STT 3.3.1: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/SpeechToText/intro
- Pleos TTS 3.3.1: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/TextToSpeech/intro
- Pleos Vehicle: https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/Vehicle/intro
- Pleos 리소스/에뮬레이터: https://developers.pleos.ai/playground/discover/resources
- Android 오디오 포커스: https://developer.android.com/media/optimize/audio-focus
- 차량용 미디어 앱: https://developer.android.com/training/cars/apps/media
- AAOS UX 제한: https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager
- Qwen2.5 1.5B: https://ollama.com/library/qwen2.5:1.5b
- Qwen3 4B: https://ollama.com/library/qwen3:4b
- Ollama thinking 제어: https://docs.ollama.com/capabilities/thinking

Pleos 문서는 ON_DEVICE 음성 사용 시 앱 등록이 필요하지 않다고 안내하지만, 실제 Pleos 에뮬레이터/차량 Speech Service가 필요하다. 차량 SDK 권한은 플랫폼의 승인 범위와 앱 심사를 따라야 한다. 일반 Android에서 SDK 라이브러리를 넣는 것만으로 차량 서비스를 사용할 수는 없다.
