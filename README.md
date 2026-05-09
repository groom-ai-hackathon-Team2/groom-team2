# 도파민드(Dopamind)

> 구름 AI 해커톤 2팀 프로젝트
> **AI가 도파민 좀비 상태를 감지해, 휴식 챌린지를 게임처럼 제안하는 안드로이드 앱**

---

## 문제 정의

- 시대가 발전할수록 숏폼 시청 시간이 증가
- 무의식적인 무한 스크롤 → 시간 감각 붕괴 / 수면의 질 저하 / 도파민 자극 과부하

## 해결 접근

| 영역 | 구현 |
| --- | --- |
| 스크롤 패턴 분석 | `AccessibilityService` 가 인스타·유튜브·틱톡의 스크롤 이벤트를 후킹 → `PatternAnalyzer` 가 5초 윈도우의 평균 간격/표준편차로 '도파민 좀비' 판정 |
| 팝업 코칭 | `OverlayService` 가 `SYSTEM_ALERT_WINDOW` 로 다른 앱 위에 코치 카드 띄움 |
| 포인트 보상/페널티 | 챌린지 수락 시 `ChallengeWorker` 가 약속 시간 후 포인트 적립. 무시할 때마다 보상 기대값이 5초마다 -10 |
| 사용자 정보 수집 | 온보딩 화면에서 직업·하루 일정 입력 → AI 코칭 프롬프트의 컨텍스트로 사용 |
| AI 적응형 난이도 | `VulnerableTimeLearner` 가 시간대별 좀비 발생 빈도를 EMA 학습 → 취약 시간대에는 임계치 완화 + 보상 3배 |
| 친구 랭킹(시연) | `assets/mock_friends.json` 6명 + 본인 점수로 정렬 |

## 사용 기술

- Kotlin 2.0 / Jetpack Compose / Material 3
- AccessibilityService / SYSTEM_ALERT_WINDOW Overlay / Foreground Service
- Room (로컬 DB) / DataStore (시간대 EMA, 온보딩 플래그)
- WorkManager (챌린지 만료 정산)
- Retrofit + OkHttp + kotlinx-serialization → **Gemini 1.5 Flash API**

## 디렉토리 구조

```
app/src/main/java/com/groomteam2/dopamind/
├── DopamindApp.kt              # Application, 알림 채널
├── MainActivity.kt             # 단일 액티비티
├── service/
│   ├── ShortFormAccessibilityService.kt
│   └── OverlayService.kt
├── analyzer/
│   ├── ScrollEventBus.kt
│   ├── PatternAnalyzer.kt
│   └── VulnerableTimeLearner.kt   # Dynamic Friction
├── ai/
│   ├── GeminiApi.kt / GeminiClient.kt
│   ├── CoachingPrompts.kt
│   └── CoachingEngine.kt          # 프롬프트 조립 + JSON 파싱
├── data/
│   ├── db/                        # Room 4개 엔티티
│   ├── prefs/UserPrefs.kt
│   └── repo/
├── work/ChallengeWorker.kt
├── di/ServiceLocator.kt           # 수동 DI (해커톤 단순화)
└── ui/
    ├── onboarding/  permission/  home/  ranking/  overlay/
    └── theme/  nav/
```

---

## 실행 튜토리얼

### 1. 사전 준비

| 항목 | 권장 버전 |
| --- | --- |
| Android Studio | **Hedgehog (2023.1.1) 이상** |
| JDK | 17 (Android Studio 내장) |
| Android SDK | API 34 (compileSdk) / API 26+ 디바이스(minSdk) |
| 디바이스 | **실기기 권장**. 에뮬레이터는 인스타·유튜브 앱이 안 깔려 있어 시연 어려움 |

### 2. Gemini API 키 발급

1. [Google AI Studio](https://aistudio.google.com/app/apikey) 접속 → Google 계정으로 로그인
2. **Create API key** → 새 키 발급
3. 발급된 키 복사 (예: `AIzaSy...`)

> 키가 없어도 앱은 실행됩니다. fallback 코칭 멘트가 자동으로 사용됩니다.

### 3. 프로젝트 열기

1. Android Studio 실행 → **Open** → `groom-team2` 폴더 선택
2. 첫 진입 시 자동으로 Gradle Sync 시작 (수 분 소요)
3. 프로젝트 루트에 `local.properties` 파일 생성 후 한 줄 추가:

   ```properties
   GEMINI_API_KEY=발급받은_키
   ```

   *Android Studio 자체가 만든 `local.properties` 가 이미 있다면 그 끝에 한 줄 더 추가하면 됨.*

4. 상단 `Build → Make Project` 으로 빌드 확인 (또는 터미널에서 `./gradlew assembleDebug`)

### 4. 디바이스 연결 & 설치

1. 실기기를 USB 디버깅 켠 채 연결
   - 설정 → 휴대전화 정보 → 빌드 번호 7번 탭 → 개발자 옵션 활성 → USB 디버깅 ON
2. Android Studio 상단 디바이스 선택 후 ▶ Run

### 5. 첫 실행 — 권한 부여

#### 5-1. 온보딩

- 직업, 하루 일정(예: `09시 출근, 18시 퇴근, 23시 취침, 토요일 영화`) 입력 후 **다음**
- 입력 정보는 AI 코치가 멘트를 만들 때 그대로 컨텍스트로 사용됨

#### 5-2. 다른 앱 위에 그리기 권한

- **허용하러 가기** 탭 → 시스템 설정에서 "도파민드" 토글 ON → 뒤로가기

#### 5-3. 접근성 서비스 권한

- **허용하러 가기** 탭 → 설정 화면에서 "다운로드한 앱" 안의 **도파민드 코치** 진입 → 토글 ON
- 시스템이 띄우는 경고 팝업에 **확인** 누름
- 뒤로 돌아오면 두 권한 모두 ✓ 표시 → **다 됐어요!** 활성화되면 탭

### 6. 시연 시나리오

1. **인스타그램 또는 유튜브 앱**을 켜고 릴스/Shorts 화면에서 **5초 안에 5번 이상 빠르게 스와이프**
2. 평균 스와이프 간격이 800ms 미만으로 떨어지면 화면 위에 **AI 코치 카드** 등장
3. 카드는 다음과 같이 동작:
   - **콜!** 누르면 → 권장 분(예: 30분) 동안 인스타·유튜브 안 켜기 챌린지 시작
   - **나중에** 또는 무시하면 → 5초마다 보상 -10 (50→40→30→20→10에서 멈춤). 30초 더 무시하면 자동 종료
4. 챌린지 시간이 끝나면 자동으로 알림 발송:
   - 약속 시간 동안 대상 앱 안 켰음 → ✅ **챌린지 성공! +50 포인트**
   - 약속 시간 안에 대상 앱 다시 켰음 → ❌ **챌린지 실패**

### 7. 홈 / 랭킹 확인

- 홈 화면에서 누적 포인트, 오늘 적립, 시간대별 위험도 막대 그래프 확인
- 같은 시간대에 4번 이상 좀비 판정이 누적되면 **취약 시간대 배지**가 등장하고, 그 시간엔 코치 보상이 3배로 책정
- 우상단 트로피 아이콘 → 친구 랭킹(mock 데이터). 본인 점수가 자동 정렬에 포함

---

## 알려진 제약 / 시연 팁

- **인스타그램이 자체 보안 패치로 접근성 이벤트를 막을 수 있음** → 시연은 **유튜브 Shorts**가 가장 안정적
- 오버레이 권한과 접근성 권한은 안드로이드 정책상 **사용자가 시스템 설정에서 직접 ON** 해야 함 (자동 부여 불가)
- API 키가 없거나 네트워크가 끊겨도 fallback 멘트로 시연 가능
- minSdk 26 (Android 8.0). 그 이하 기기는 동작 보장 안 함
- 친구 랭킹은 `assets/mock_friends.json` 의 6명 데이터로 시뮬레이션 (서버 미연동)

## 프라이버시

- 접근성 서비스의 `canRetrieveWindowContent=false` 로, 화면 내용(텍스트/이미지)은 **절대 수집하지 않음**
- 후킹하는 정보는 **스크롤 이벤트의 발생 시각과 패키지명**뿐
- 사용자 입력(직업/일정), 포인트 내역은 **모두 기기 로컬 DB**에 저장. 서버 전송 없음
- AI 코칭 프롬프트에 사용자 정보가 포함되어 Gemini로 전송됨 — 한국어 코칭 멘트 생성 외 다른 용도로 사용되지 않음

## 라이선스

해커톤 시연용 프로토타입. 외부 배포 시 Google Gemini 사용 약관 준수 필요.
