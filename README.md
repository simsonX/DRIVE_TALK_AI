# DRIVE TALK AI

출퇴근 시간을 활용하는 오디오 영어 학습 **Android 프로토타입**입니다. Kotlin·Jetpack Compose·Room·Media3와 로컬 Qwen3 4B를 사용합니다.

**현재 실행 환경은 일반 Android 에뮬레이터의 DEMO입니다.** 실제 Pleos 차량 상태·음성 서비스와의 런타임 연결은 완료하지 않았습니다. Pleos SDK 어댑터는 별도 모듈에서 컴파일 검증했습니다.

## 팀원 빠른 확인

- [설치용 APK 및 릴리스 노트](https://github.com/simsonX/DRIVE_TALK_AI/releases/tag/v0.1.0-prototype)
- [Android 프로젝트 · 설치/실행 방법](DRIVE_TALK_AI/README.md)
- [시연 순서](DRIVE_TALK_AI/docs/DEMO.md)
- [시스템 구조 · SRS · AI 계약](DRIVE_TALK_AI/docs/ARCHITECTURE.md)
- [실행한 테스트 · 실제 검증 범위 · 제약](DRIVE_TALK_AI/docs/VALIDATION.md)
- [개발 기록](DRIVE_TALK_AI/docs/CHANGELOG.md)

Android Studio에서는 저장소 루트 아래 **`DRIVE_TALK_AI/` 폴더**를 프로젝트로 엽니다.

## 구현 기능

- 비즈니스·일상·여행, 초급·중급·고급의 기본 예문 54개.
- 3·5·10·15개 문장 학습. 느린 영어/한국어 음성, 반복·다음·일시정지·재개·종료.
- 언어 사이 2초 쉼, 문장 사이 기본 8초(5·8·12초 선택)의 따라 말하기 시간.
- Room 학습 기록과 직접 평가 기반 SRS. 청취만으로 정답 처리하지 않습니다.
- 출근 새 표현 / 퇴근 개인화 복습.
- 실제 로컬 Qwen 예문 생성과 주차 회화·교정 제안. 최근 문장 중복 방지 및 연속 재생성.
- UNKNOWN/DRIVING에서 회화·상세 기록 제한. 수동 상태는 DEMO로 표시합니다.

## 로컬 AI 실행

모델 파일과 API 키는 저장소에 포함하지 않습니다. Ollama가 실행 중인 개발 PC에서:

```sh
ollama pull qwen3:4b
cd DRIVE_TALK_AI
python3 ai-server/server.py
```

Android 에뮬레이터는 개발 PC의 `http://10.0.2.2:8765`에 연결합니다. 모델은 **차량 내부가 아닌 개발 PC**에서 실행됩니다. 서버 없이도 기본 예문 학습은 가능하고, Qwen 생성·회화에는 서버가 필요합니다.

## APK 시연

릴리스의 debug APK를 에뮬레이터 또는 Android 9 이상 기기에 설치합니다. 기기의 영어·한국어 TTS 음성 데이터가 필요합니다.

1. 앱 설정에서 **DEMO 주차**를 선택합니다(일반 Android debug 환경).
2. 수준·카테고리·문장 개수를 선택하고 출근길 학습을 실행합니다.
3. `Qwen 예문 생성` 후 `다른 예문 생성`으로 연속 생성을 확인합니다.
4. AI 영어회화에서 텍스트 답변 또는 기기 STT를 사용합니다. 실제 사람의 발화 인식 정확도는 아직 검증하지 않았습니다.

실제 Android 기기에서 Qwen을 사용하려면 서버 주소/ADB 연결 설정이 별도로 필요합니다. 현재 기본 주소는 Android 에뮬레이터용입니다. 학습 기록은 기기에 저장하며 앱에서 삭제할 수 있습니다.

## 검증

최신 재생성 수정본 기준 Kotlin 단위 테스트 **13개**, Python 테스트 **14개**, Android 기기 테스트 **10개**가 통과했습니다. Android 테스트에는 로컬 Qwen을 세 번 연속 호출하여 다른 문장을 반환하는 실제 추론 검사가 포함됩니다. 상세 환경과 실패 후 재검증 기록은 [검증 문서](DRIVE_TALK_AI/docs/VALIDATION.md)를 참고하세요.

배포용 서명·Pleos 실차 인증·차량 미디어 호스트 심사는 수행하지 않았습니다. 릴리스 APK는 팀 시연용 debug 빌드입니다.
