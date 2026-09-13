# Japanese Design Guide v2

최종 개정: 2026-09-12
적용 범위: Japanese 웹과 향후 Android 클라이언트의 정보 구조, 화면 설계, 공통 컴포넌트, 시각 언어

## 1. 제품 경험

Japanese는 일본어 단어와 문법을 찾고, 이해하고, 매일 정해진 양만큼 학습하고, 적절한 시점에 복습하여 장기 기억으로 만드는 서비스다. 퀴즈, EXP, Streak, Haru는 이 과정을 설명하고 지속하게 돕는다.

새 디자인의 핵심 문장은 **“매일 돌아오는 일본어 학습 데스크”**다.

- 학습 앱처럼 다음 행동이 명확해야 한다.
- 사전처럼 찾는 속도와 정보의 정확성이 높아야 한다.
- 학습 기록은 숫자 전시가 아니라 다음 행동을 결정하게 해야 한다.
- Haru는 홈에 함께 머무는 동료이되 학습 내용을 가리지 않는다.
- 게임 코스맵과 업무용 SaaS 대시보드처럼 보이지 않아야 한다.

## 2. 기존 디자인 감사

### 유지할 원칙

- 단어·문법 콘텐츠를 캐릭터와 게임 요소보다 우선한다.
- 빈 섹션, 과도한 카드, 큰 여백을 만들지 않는다.
- 검색·상세 조회만으로 학습 상태를 변경하지 않는다.
- 로그인 전에도 검색과 공개 콘텐츠 탐색을 제공한다.
- 모바일을 데스크톱의 단순 세로 축소판으로 만들지 않는다.
- 실제 데이터가 있는 요소만 렌더링한다.
- `prefers-reduced-motion`을 존중한다.
- Word와 Grammar는 서로 다른 정보 구조를 사용한다.

### 버릴 패턴

- 홈 안에 전체 검색 결과 목록과 개인 대시보드를 함께 넣는 구조
- 모든 개인 기능을 1차 내비게이션에 나열하는 구조
- 작은 정보마다 독립 카드와 테두리를 만드는 구조
- 영문 eyebrow를 모든 섹션 제목 위에 반복하는 표현
- 베이지·세이지 색조 자체를 브랜드 정체성으로 고정하는 방식
- 큰 캐릭터 장면을 만든 뒤 남은 공간에 학습 정보를 끼워 넣는 방식
- 통계 숫자를 같은 크기의 카드 여러 개로 나열하는 방식
- 데스크톱 구성을 그대로 한 열로 쌓는 모바일 대응
- 같은 중요도의 버튼을 한 화면에 여러 개 강조하는 방식

### 다시 정의할 원칙

- **컴팩트함:** 작게 만드는 것이 아니라 관련 정보와 다음 행동을 한 덩어리로 묶는다.
- **캐릭터 존재감:** 이미지 크기보다 장면, 상태 문구, 학습 진행과의 연결로 만든다.
- **진도:** EXP와 JLPT 학습 상태를 분리한다. EXP는 Haru 성장, JLPT 진도는 콘텐츠 숙달이다.
- **카드:** 선택, 행동, 독립 상태가 있는 정보에만 쓴다. 나머지는 section과 divider로 묶는다.
- **홈:** 모든 기능의 축소판이 아니라 오늘 해야 할 일과 최근 맥락을 복원하는 화면이다.

## 3. 레퍼런스에서 채택한 패턴

한 제품의 외형을 복제하지 않고 역할별 패턴만 채택한다.

| 제품군 | 관찰한 패턴 | Japanese 적용 |
| --- | --- | --- |
| Duolingo | 홈의 다음 행동이 하나로 명확하고 복습도 학습 경로에 포함 | 대표 CTA를 `오늘 학습 이어하기` 하나로 제한 |
| Busuu | 목적·수준·학습 계획을 짧게 설정하고 약점을 상태별로 표시 | 온보딩 4단계, 실제 기록에 근거한 약점 이유 |
| Bunpro | 문법 설명·예문·SRS·관련 문법을 연결하고 세션을 재개 | Grammar 상세에서 비교·confirmation·재학습 연결 |
| WaniKani | 지금 처리할 복습과 전체 부담을 분리 | 오늘 몫과 전체 backlog를 분리 |
| Quizlet | 미학습·학습 중·숙달 집합에서 바로 학습 | 진도와 약점 상태를 행동으로 연결 |
| LingoDeer | 새 학습과 SRS를 구분하면서 콘텐츠 맥락 유지 | Today 안에서 유형을 명확히 표시 |
| Drops | 짧고 부담이 낮은 일일 세션 | 온보딩과 홈에 예상량을 구체적으로 표시 |
| Takoboto 등 사전 앱 | 즉시 검색, 표기·읽기·뜻의 강한 위계 | 검색을 독립 목적지로 만들고 결과 행을 고밀도로 구성 |

참고:

- [Duolingo 홈 학습 경로](https://blog.duolingo.com/new-duolingo-home-screen-design/)
- [Duolingo 핵심 탭 리디자인](https://blog.duolingo.com/core-tabs-redesign/)
- [Busuu Study Plan](https://www.busuu.com/en/english/personalized-study-plan-busuu-premium)
- [Busuu Grammar Review](https://blog.busuu.com/grammar-review-web-release/)
- [Bunpro 기능과 학습 흐름](https://bunpro.jp/ja/news)
- [Quizlet 상태별 학습](https://help.quizlet.com/hc/en-us/articles/360048803491-Using-Progress-for-targeted-studying)
- [LingoDeer SRS](https://lingodeer.freshdesk.com/support/solutions/articles/61000319118-how-does-the-spaced-repetition-system-srs-work-)
- [Drops 일일 학습](https://languagedrops.com/welcome)
- [Takoboto 검색](https://play.google.com/store/apps/details?id=jp.takoboto)

## 4. 정보 구조

### 최상위 목적지

1. **홈:** 오늘 학습, Haru, 현재 리듬, 이어보기
2. **오늘:** 고정된 오늘 세션을 시작·재개·완료
3. **사전:** 단어·문법 검색, 필터, 상세
4. **복습:** 복습 대기, 자유 복습, 약점노트
5. **내 학습:** 진도, 주간 리포트, 기록, 저장함, 설정

`/study`는 복습 허브의 자유 카드 학습으로, `/quiz`는 내 학습 안의 보조 확인 문제로 설명한다. 둘을 `/today`와 동급의 주 학습 시작점처럼 노출하지 않는다.

### 데스크톱

- 상단 바: 브랜드, 전역 검색, `오늘 학습` 버튼, 프로필
- 왼쪽 레일: 홈 / 오늘 / 사전 / 복습 / 내 학습
- 복습 2차: 복습 대기, 약점노트
- 내 학습 2차: 진도, 주간 리포트, 기록, 저장함
- 북마크·학습 큐·컬렉션은 `저장함` 내부 탭으로 묶되 데이터 의미는 유지한다.
- 설정과 로그아웃은 프로필 메뉴에 둔다.
- 관리자 검토 메뉴는 일반 학습 내비게이션과 분리한다.

기본 본문 폭은 960px, 읽기 상세는 880px, 리포트·진도는 1040px까지 허용한다. 앱 쉘 최대 폭은 1440px다.

### 모바일

하단 탭을 홈 / 오늘 / 사전 / 복습 / 내 학습 5개로 고정하고 레이블을 항상 표시한다.

- Today 학습 중에는 하단 탭을 숨기고 종료·진행·응답만 남긴다.
- 내 학습 첫 화면은 진도, 주간 리포트, 기록, 저장함, 설정의 compact 목록이다.
- 검색 필터는 bottom sheet 또는 전체 폭 drawer를 쓴다.
- 로그인 전에는 브랜드 / 사전 / 로그인 / 시작하기만 제공한다.

## 5. 시각 언어

색은 일본 전통색을 장식으로 소비하지 않고 종이와 잉크의 대비에서 가져온다.

| Token | 값 | 용도 |
| --- | --- | --- |
| `--canvas` | `#F7F6F2` | 앱 배경 |
| `--surface` | `#FFFFFF` | 주요 표면 |
| `--surface-subtle` | `#F0EFEA` | 보조 그룹 |
| `--ink` | `#202321` | 본문과 제목 |
| `--ink-muted` | `#676D68` | 부가 정보 |
| `--line` | `#DADDD7` | 구분선 |
| `--indigo-700` | `#334B78` | 브랜드, 주 행동 |
| `--indigo-100` | `#E8EDF6` | 선택 배경 |
| `--vermilion-600` | `#C5523F` | 오답, 주의 |
| `--matcha-600` | `#52745B` | 정답, 숙달 |
| `--amber-500` | `#B8782D` | 복습 대기, streak |
| `--focus` | `#3569C8` | 키보드 포커스 |

- 한 화면의 강한 색은 대표 행동 하나와 상태 표현에만 쓴다.
- JLPT N5~N1은 텍스트가 있는 동일 계열 chip으로 구분한다.
- 다크 모드는 v2 1차 범위에서 제외하되 토큰 확장성은 둔다.

### Typography

- UI: `Pretendard Variable`, `Noto Sans KR`, system sans-serif
- 일본어: `Noto Sans JP`, `Yu Gothic UI`, sans-serif
- 숫자: tabular numerals

| Style | Desktop | Mobile |
| --- | --- | --- |
| Display | 32/40, 700 | 27/35, 700 |
| H1 | 28/36, 700 | 24/32, 700 |
| H2 | 20/28, 700 | 18/26, 700 |
| H3 | 16/24, 650 | 16/23, 650 |
| Body | 15/24, 400 | 15/24, 400 |
| Japanese headword | 34/44, 650 | 30/40, 650 |
| Japanese example | 19/32, 500 | 18/31, 500 |
| Small | 12/18, 500 | 12/18, 500 |

### Spacing과 표면

Spacing scale은 4, 8, 12, 16, 24, 32, 48, 64다.

- 화면 좌우: phone 16px, tablet 24px, desktop 32px
- 섹션 간격: 32px, 큰 문맥 전환만 48px
- 카드 내부: 16px, 학습·상세 읽기 표면 20~24px
- 버튼 44px, compact 36px, 터치 영역 최소 44×44px
- 입력 44px, 전역 검색 46px
- radius: 입력·버튼 10px, 표면 14px, pill 999px
- 관련 행은 하나의 표면 안에서 divider로 나눈다.
- 그림자는 팝오버, sticky action bar, 캐릭터 전경에만 쓴다.

## 6. 공통 컴포넌트

### App shell

- 데스크톱 헤더 64px, 레일 200px
- 모바일 헤더 52px, 하단 탭 64px + safe area
- breadcrumb는 상세·비교처럼 실제 상위 맥락이 있을 때만 쓴다.

### Actions

- Primary: 화면의 다음 핵심 행동 하나
- Secondary: 같은 맥락의 대안
- Quiet: 저장, 초기화, 보조 이동
- Destructive: 삭제 확인 안에서만 강조
- 아이콘 버튼은 accessible name과 tooltip을 갖는다.

### Status

- 콘텐츠: WORD / GRAMMAR
- 학습: 새 콘텐츠 / 학습 중 / 복습 중 / 숙달 / 보류
- 활동: 새 학습 / 복습 / 재학습 / 확인 문제
- 색만으로 전달하지 않고 텍스트를 항상 포함한다.

### Progress

- 목표: bar + `완료/목표`
- SRS 분포: stacked bar + 범례
- 7일 활동: 작은 세로 bar 또는 dot row
- 원형 차트는 기본 패턴으로 쓰지 않는다.

### Empty, loading, error

- 빈 상태에는 이유와 가능한 다음 행동만 둔다.
- skeleton은 최종 레이아웃과 같은 구조다.
- 오류는 실패한 행동 가까이에 표시하고 입력값을 보존한다.

## 7. Haru 통합

Haru는 홈의 분위기, 학습 상태 반응, EXP 성장 피드백을 담당한다. JLPT 진도를 대신 설명하지 않는다.

- 데스크톱 장면은 홈 첫 영역의 32~38% 폭
- 모바일 장면은 높이 140~180px 안에서 안정적인 crop 사용
- 장면 옆 또는 아래에 Today CTA, 목표 진행, EXP를 함께 배치
- 배경 소품은 2~3개로 제한
- `idle`, `study`, `happy`, `goal-complete`, `growth` 상태를 실제 학습 사건과 연결
- idle 동작은 8~18초의 불규칙 간격으로 한 가지씩 실행
- PNG 전체를 계속 흔들거나 상하 이동하지 않음
- reduced motion에서는 반복 동작을 중지하고 상태 프레임만 전환

## 8. 핵심 화면

### Home

목표: 5초 안에 오늘 할 일과 현재 리듬을 이해하고 시작한다.

1. 짧은 환영과 학습 범위
2. Haru scene + 오늘 학습 패널
3. 복습 / 새 단어 / 새 문법, backlog 보조 표시
4. 대표 CTA: 시작 또는 이어하기
5. 7일 리듬과 최근 이어볼 콘텐츠
6. 학습 플랜 수정 링크

모바일 CTA는 첫 viewport 안에 둔다. 최근 퀴즈는 홈에서 제거하고 기록으로 이동한다.

### Today Study

- 상단: 닫기, `현재/전체`, 전체 progress
- 보조 행: 복습·새 단어·새 문법 남은 수
- 카드: 활동 유형, 콘텐츠 유형, JLPT, 기존 Word/Grammar 학습 정보
- 하단 sticky action: `다시 볼게요` / `기억했어요` 또는 `이해했어요`
- 상세 이동 후 돌아올 세션 위치를 보존
- Grammar confirmation은 완료 후 선택 행동이며 progress와 EXP를 추가하지 않음
- 마지막 응답 뒤 실제 기록 기반 결과 화면으로 이동

### Search & Results

데스크톱은 sticky 검색, 왼쪽 200px 필터, 오른쪽 compact 결과 목록으로 구성한다. 결과 행은 유형, 표기/패턴, 읽기, 대표 의미, JLPT/분야 순이다.

모바일은 검색 아래 유형 segmented control과 `필터 n` bottom sheet를 쓴다. 결과는 2~3줄, badge는 최대 2개 후 `+n`으로 줄인다.

### Word Detail

- 표제어, 읽기, 품사, pitch, JLPT/분야, 학습 상태
- 번호가 있는 Meaning
- 각 Meaning 아래 연결 Example
- 미연결 Example은 `추가 예문`으로 보존
- 데스크톱 우측 sticky rail: 학습 큐, 북마크, 컬렉션, 학습 시작
- 모바일 하단 action sheet
- 값이 없는 필드는 렌더링하지 않음

### Grammar Detail

- Pattern, 핵심 의미, JLPT/분야, 학습 상태
- 접속
- source example: 일본어 → 읽기 → 번역
- approved curated 정보: 사용 상황 → nuance → formation supplement → 주의/실수
- 관계가 있는 관련 문법 1~3개
- comparison과 confirmation은 별도 행동
- provenance는 낮은 위계로 확인 가능하게 유지
- Word 상세와 같은 템플릿을 쓰지 않음

### Weaknesses

- 현재 약점 / 개선됨 탭, WORD / GRAMMAR 필터
- `약점 10개 집중 복습` CTA와 진행 세션 재개
- 항목마다 표제어, 상태, 근거 chip, 마지막 오답 시각
- 관련 상세와 문법 비교 링크
- 개선됨은 성공 기록과 개선 시점을 표시하고 기본적으로 접음
- 근거 없는 약점 점수를 만들지 않음

### Weekly Report

- 첫 문장: `5일 학습 · 42개 처리 · 정답률 81%`
- 7일 bar: 정규 학습을 중심으로 RETRAIN을 보조색으로 표시
- 새 단어/문법, 복습, EXP는 compact definition list
- Quiz와 grammar confirmation은 보조 활동으로 분리
- 실제 변화가 있는 JLPT progress만 표시
- 많이 틀린 항목과 개선된 약점
- 하단 2~3개 결정론적 다음 행동
- 이전 7일 대비는 `+8개`, `-2일`처럼 단위를 표시

### Progress

- 내 학습 범위를 상단에 표시
- N5~N1은 compact section 또는 accordion
- Level마다 WORD / GRAMMAR 두 행
- 진행률, stacked status bar, 미학습·학습 중·복습 중·숙달·보류 수
- 계속 학습은 Today, 큐, 필터된 자유 학습으로 연결
- 범위 밖 Level도 탐색 가능하되 보조 위계

### Onboarding

2분 이내에 마치는 4단계다.

1. 목적: JLPT 또는 실제 Category
2. 현재 수준과 목표 Level
3. 학습량 preset + 직접 조정
4. 플랜 확인과 시작

- `1/4`와 짧은 progress를 표시
- 한 단계에 한 종류의 결정만 배치
- 모바일 한 열, 데스크톱 2~3열 radio card
- `잘 모르겠음` 처리 방식을 설명
- 완료 화면의 primary는 `오늘 학습 시작`
- 중간 단계에서 학습 상태를 생성하지 않음

## 9. 보조 화면

- **저장함:** 북마크 / 학습 큐 / 컬렉션 탭. 각 의미를 첫 진입에 한 줄로 설명한다.
- **History:** 월 activity calendar + 선택 날짜 상세. Weekly Report와 역할을 분리한다.
- **Statistics:** 유지한다면 장기 누적 통계로 한정하고 주간 정보는 Weekly Report로 이동한다.
- **Quiz:** Today 밖의 선택 확인 활동이다.
- **Settings:** 프로필 / 학습 플랜 / 캐릭터 / 계정 section.
- **Login/Signup:** 단일 column, 입력과 오류 우선. 큰 마케팅 패널은 쓰지 않는다.

## 10. Responsive

Breakpoints: phone 0–599, large phone/tablet 600–899, compact desktop 900–1199, desktop 1200+.

- 900px 미만에서 왼쪽 레일을 하단 탭으로 교체
- 600px 미만에서 상세 sticky rail을 bottom action으로 교체
- 학습 응답은 phone safe area 위에 고정
- 표는 600px 미만에서 definition list로 변환
- 문법 비교는 2열 표 대신 A/B section과 상단 차이 요약
- filter drawer, dialog, bottom sheet에 focus trap과 ESC/뒤로가기 제공
- 320px에서 일본어 문장과 action의 가로 overflow 금지

## 11. 접근성과 문장

- 본문 대비 4.5:1, 큰 텍스트 3:1 이상
- 모든 focusable 요소에 2px focus ring
- 상태는 색, 모양, 텍스트로 함께 전달
- 일본어 텍스트에 `lang="ja"`
- 버튼은 `시작`, `이어하기`, `다시 볼게요`, `기억했어요`처럼 결과를 표현
- 죄책감을 유도하거나 streak 손실을 위협하지 않음

## 12. 구현 순서와 검수

구현 순서:

1. 토큰, App shell, desktop rail, mobile tabs
2. Home, Today
3. Search, Word Detail, Grammar Detail
4. Weaknesses, Weekly Report, Progress
5. Onboarding, 저장함, 설정, 인증
6. motion과 character polish

매 화면에서 확인한다.

- 첫 viewport에서 대표 행동을 찾을 수 있는가
- primary button이 둘 이상 경쟁하는가
- 데이터가 없는데 빈 section이 보이는가
- 모든 section이 카드일 필요가 있는가
- 숫자가 설명 또는 다음 행동과 연결되는가
- 320px에서 예문과 action이 겹치지 않는가
- 키보드만으로 주요 흐름을 완료할 수 있는가
- reduced motion에서 의미가 사라지지 않는가
- 검색·상세 조회가 학습 데이터를 변경하지 않는가

## 13. 디자인 파일 구조

Figma 파일은 다음 page 구조를 사용한다.

- `00 Foundations`: color, type, spacing, grid, icon
- `01 Components`: shell, buttons, fields, chips, progress, rows, action bars
- `02 Desktop`: 9개 핵심 화면, 1440px
- `03 Mobile`: 9개 핵심 화면, 390px
- `04 Flows`: onboarding, today resume/complete, search-to-study

화면 이름은 `Desktop/Home`, `Mobile/Home`처럼 고정하고 상태 variant는 `Empty`, `Active`, `Complete`, `Error` suffix를 사용한다.

현재 세션에서 Figma 플러그인은 설치 상태지만 실행기 도구가 연결되지 않아 파일을 생성하지 못했다. 실행기 연결 후 이 구조와 화면 명세를 그대로 Figma auto layout 및 component로 옮긴다.
