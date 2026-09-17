# Japanese 개발 현황

최종 정리: **2026-09-17**

Japanese는 JLPT 단어와 문법을 탐색하고, 새 콘텐츠를 학습한 뒤 SRS 복습으로 장기 기억을 돕는 웹 서비스다. 퀴즈, EXP, Streak, Haru 캐릭터는 학습을 보조하며 학습 흐름 자체를 대체하지 않는다. 현재 웹 화면과 REST API는 같은 Service 계층을 사용하므로 Android 클라이언트를 추가해도 핵심 규칙을 다시 구현할 필요가 없다.

## 실행과 설정

- 기본 주소: `http://localhost:8080`
- 기본 프로필: `sample`
- 비밀값: `japanese/secrets.yml`에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 설정한다. 파일은 Git에 포함하지 않는다.
- 기본 일일 목표: `japanese.learning.daily-goal=10`
- 학습 날짜와 Streak 기준 시간대: `Asia/Seoul`

```powershell
cd japanese
.\gradlew.bat bootRun --args="--spring.profiles.active=sample"
```

## 콘텐츠와 데이터 모델

`ContentItem`이 공개 여부, 유형, 출처, 검토 상태를 갖는 공통 루트다. `WORD`는 `Word`와 1:1이고 `Word`는 여러 `Meaning`을 가진다. `GRAMMAR`는 `Grammar`와 1:1이다. `Example`은 콘텐츠에 연결되며 필요하면 특정 `Meaning`을 참조한다. `Level`과 `Category`는 콘텐츠와 다대다 관계이므로 JLPT 외에 IT, 호텔, 비즈니스, 경어 등도 같은 구조로 추가할 수 있다.

- `Word`: 표기, 읽기, 품사, 피치 악센트
- `Meaning`: 언어 태그, 뜻, 의미 순서
- `Example`: 일본어 문장, 읽기, 번역, 표시 순서, 선택적 Meaning 연결
- `Grammar`: 패턴, 설명, 접속
- `ContentSource`: 출처, 버전, 라이선스 요약, 표시 문구
- `ContentReviewHistory`: 공개·반려·검토 이력

공개 검색과 상세에는 `published=true` 콘텐츠만 노출된다. PENDING과 REJECTED 콘텐츠는 관리자 검토 화면에서만 다룬다.

## 계정과 보안

- 회원가입: 로그인 ID, 표시 이름, 비밀번호 검증, 중복 가입 방지
- Spring Security 폼 로그인과 로그아웃
- BCrypt 비밀번호 해시 저장
- 검색, 콘텐츠 목록·상세, 카테고리, 공개 퀴즈 조회는 비로그인 허용
- 학습, 북마크, 통계, 사용자별 학습 상태 API는 인증 필요
- 기존 `learnerKey`는 클라이언트 입력값으로 받지 않으며 인증된 `UserAccount`에서만 사용자 학습 데이터를 조회한다.

## 학습과 복습

### 오늘의 학습

`/today`는 정규 일일 학습 흐름이다. 복습 예정 콘텐츠를 먼저 배치하고, 남은 목표 범위 안에서 새 단어와 새 문법을 배치한다. 첫 노출은 문제를 내지 않고 표기·읽기·뜻·품사·예문 또는 문법 패턴·설명·접속·예문을 읽는 학습 화면으로 제공한다.

사용자별 `LearnerStudyPreference`에는 학습 범위(JLPT 레벨과 카테고리), 하루 새 단어 수, 하루 새 문법 수가 저장된다. 기본 새 학습 상한은 단어 5개와 문법 5개다. 설정이 없는 기존 사용자는 기본값으로 동작한다.

### SRS와 backlog

- `LearningProgress` 상태: `LEARNING`, `REVIEW`, `MASTERED`, `SUSPENDED`
- 정답 간격: 연속 정답 기준 1, 2, 4, 8, 16일
- 연속 정답 5회면 `MASTERED`
- 오답은 연속 정답을 초기화하고 10분 뒤 다시 복습
- 복습 backlog는 오래 밀린 카드부터 처리하지만, 오늘의 처리량은 일일 목표를 넘겨 무제한으로 강제하지 않는다.
- 오늘 계획은 오늘 처리할 복습 수, 전체 대기 복습 수, 다음으로 넘길 수량을 함께 보여 준다.

### 중복 제출과 재학습

`StudyRecord`는 `sessionKey`와 `activityType`을 가진다. 같은 사용자·세션·콘텐츠의 완료 요청은 서버에서 한 번만 반영하므로 새로고침이나 재전송으로 StudyRecord, EXP, 목표, Streak, SRS가 중복 증가하지 않는다.

`/study`는 자유 카드 학습과 복습이고, `/quiz`는 선택형 확인 문제다. 북마크와 반복 오답 콘텐츠는 `/study/relearn/bookmarks`, `/study/relearn/weaknesses`에서 정규 오늘 학습과 분리해 다시 학습할 수 있다. 재학습은 SRS와 Streak에 반영하지만 EXP와 정규 일일 목표에는 반영하지 않아 반복 파밍을 막는다.

## 개인화와 통계

- 사용자별 일일 목표, EXP, 레벨, 캐릭터 선택, 가입일, 최근 학습일
- 오늘 학습량, 목표 진행률, 복습 대기 수, 최근 학습, 최근 퀴즈
- 총 학습 횟수, 정답·오답, 정답률, 누적 EXP, 최근 7일 학습량, JLPT별 현황
- 반복 오답과 최근 오답을 약점 콘텐츠로 제공
- 한국 시간 기준 현재·최고 Streak와 최근 7일 학습일 표시

Haru는 `young`, `apprentice`, `confident`, `reliable` 성장 단계를 가진다. 성장 기준은 JLPT 레벨이 아니라 누적 EXP다. 캐릭터 상태는 `idle`, `study`, `happy`, `goal-complete`, `growth`로 확장 가능하게 구성되어 있다.

## 검색과 사전

### 검색 규칙

검색 키는 원문을 바꾸지 않고 별도 컬럼에 저장한다.

- Word: 표기와 읽기
- Meaning: 다국어 뜻
- Grammar: 패턴, 설명, 접속
- Example: 일본어 문장, 읽기, 번역
- Category: 이름과 slug

검색어는 앞뒤 공백을 제거하고 Unicode NFKC 정규화, 일반 공백 제거, 가타카나→히라가나 변환, 문법 물결표 표기 통합을 적용한다. 따라서 `食べる`, `たべる`, `タベル`, `먹다`, 일부 문자열, `～てもいい` 같은 입력을 지원한다. 로마자 검색은 신뢰할 수 있는 저장 데이터나 변환기가 없어 의도적으로 지원하지 않는다.

검색 우선순위는 정확한 표기·문법 패턴, 정확한 읽기, 표기·읽기·패턴 접두 일치, 정확한 뜻, 문법 설명·접속, 예문·카테고리 등 보조 일치 순서다.

`ContentItemRepository.searchPublished`가 검색, 타입, JLPT, 카테고리 필터, 정렬, pagination을 DB에서 처리한다. 컬렉션 조인으로 페이지가 중복되지 않도록 상관 `exists` 조건을 사용한다. 검색 키와 `published/type`에 필요한 인덱스를 선언했으며, 기존 행의 검색 키는 시작 시 250개 단위로 backfill한다.

### 상세 화면

단어 상세는 표기, 읽기, 품사, JLPT·분야, 여러 의미, 의미별 예문, 의미와 연결되지 않은 예문, 피치 악센트, 출처를 표시한다. `Example.meaning`이 연결된 예문은 해당 뜻 아래에 보이고, 연결되지 않은 예문은 별도 섹션에서 유실 없이 보인다.

문법 상세는 패턴, 의미·설명, 접속, 예문, 읽기, 번역 순서로 제공한다. 근거 없는 문자열 유사도 기반 관련 문법 기능은 만들지 않았다.

숫자 피치 악센트는 하강 위치로 읽기 쉽게 표시한다. `terminal=...;mora=...` 또는 해석 근거가 없는 원본은 추측 변환하지 않고 원문을 보존해 펼쳐 볼 수 있다.

로그인한 사용자는 상세에서 `새 콘텐츠`, `학습 중`, `복습 중`, `숙달`, `학습 보류` 상태와 다음 복습 시각을 확인한다. 검색과 상세 조회는 `LearningProgress`, StudyRecord, EXP, 일일 목표, Streak를 변경하지 않는다.

## 주요 API

- `GET /api/v1/contents`: 공개 콘텐츠 검색
- `GET /api/v1/contents/page`: DB pagination 검색
- `GET /api/v1/contents/{slug}`: 공개 콘텐츠 상세
- `GET /api/v1/contents/filters`: 타입·레벨·카테고리 필터
- `GET /api/v1/study/today`: 인증 사용자 오늘 학습 계획
- `GET/POST /api/v1/study/scope`: 사용자 학습 범위
- `GET/POST /api/v1/study/preferences`: 새 단어·문법 일일 설정
- `GET /api/v1/study/contents/{slug}/status`: 인증 사용자 콘텐츠 학습 상태
- `GET /api/v1/study/relearn/{target}`: 북마크·약점 재학습 카드

## 화면과 UI

공통 header, navigation, 버튼, 입력, badge, card, 진행 상태는 같은 CSS 시스템을 사용한다. 콘텐츠 목록은 많은 결과를 빠르게 훑을 수 있도록 컴팩트하게 제공하고, 상세는 사전과 학습 자료 사이의 정보 위계를 갖는다. `/today`는 콘텐츠 중심이며, 홈은 캐릭터·오늘 목표·EXP·Streak·복습 대기를 함께 보여 준다. 모바일에서도 긴 일본어 문장, 필터, navigation, 버튼 overflow를 고려한다.

## 검증 상태

최신 실행: `./gradlew.bat test`

- 총 48개 테스트
- 통과 47개
- 조건부 제외 1개: 실제 APKG 경로가 있을 때만 실행하는 import smoke test
- 실패 0개, 오류 0개

검색 검증에는 한자·히라가나·가타카나·한국어 의미·문법 패턴·예문 검색, 복합 필터, 정확 일치 우선순위, DB pagination, PENDING·REJECTED 비공개, 다중 Meaning, 연결·미연결 Example, 피치 표시, 익명 검색·상세, 읽기 전용 학습 상태를 포함한다.

## 다음 작업

1. 운영 배포 전 Flyway 같은 버전형 DB 마이그레이션을 도입한다. 현재는 `ddl-auto=update`와 nullable 검색 키 backfill을 사용한다.
2. 전체 콘텐츠를 넣은 뒤 MySQL 검색 실행 계획과 응답 시간을 측정한다. 선행 와일드카드 부분 검색은 사전 UX를 위해 유지하므로, 실제 지표가 필요할 때만 Full-text 전략을 검토한다.
3. 출처 데이터에 피치 악센트 원본 형식 문서가 확보되면 검증 가능한 파서를 추가한다.

## JLPT MAX 2.1.1 전체 import 감사

실제 `JLPT-MAX-Deck-2.1.1.apkg`를 다시 읽어 모델 정의와 필드 분포를 [jlpt-max-apkg-audit.json](/C:/Users/icand/git/japanese/docs/jlpt-max-apkg-audit.json)에 저장했다. 원본은 Anki SQLite `collection.anki21`이며, custom `unicase` collation을 사용한다.

- vocab 모델: 9,160 notes, 59 fields. 실제 원천은 `EntryID`, `Word`, `Reading`, `PitchAccent`, `Meaning`, `ExamplesRendered`, `PartOfSpeech`, `JLPT`/`WordJLPT`다. 개별 `Example1..5` 필드는 전체 비어 있고 rendered HTML만 예문·읽기·번역·뜻 label을 가진다. retired tag 1건은 제외했다.
- grammar 모델: 3,605 notes, 9 fields. `Level`, `UnitID`, `FrontHTML`, `BackHTML`이 실제 원천이다. `Kind`는 전부 비어 있어 `UnitID`를 접속 형태로 위장하지 않고 connection은 비워 둔다.
- quiz 7,876 notes와 reference 9 notes는 현재 콘텐츠·퀴즈 도메인에 자연스럽게 연결되지 않아 학습 콘텐츠로 import하지 않는다. 기존 raw metadata가 있을 수는 있지만 새 전체 import의 기본 대상도 아니다.

vocab는 단어·읽기·품사·raw pitch·여러 Meaning·예문을 저장한다. slash는 공백을 가진 sense separator일 때만 분리하며, `aria-label`의 뜻 label과 정확히 일치한 예문만 Meaning에 연결한다. 불일치 예문은 unlinked로 보존한다. grammar는 패턴·설명·실제 예문·ruby reading·번역을 저장한다.

사용자 표시 문자열에는 Jsoup 기반 Anki HTML normalizer를 적용한다. ruby의 `rt`, Anki U+2063, zero-width 문자, entity, `<br>`/block whitespace를 정리하되 raw Anki field는 `ImportedSourceRecord`에 보존한다. 기존 검색 key는 엔티티 생성 시 같은 normalizer 규칙으로 생성된다.

재실행 식별자는 `(sourceRef, noteType, sourceNoteId)` unique key이며 `ImportedSourceRecord.contentItem`으로 연결한다. URL slug는 vocab의 안정적 `EntryID`와 grammar의 source note id를 사용한다. 100건씩 transaction으로 저장·flush하며, 이미 연결된 note는 skip하고 이전 importer로 저장된 콘텐츠는 재실행에서 source link만 보완한다. malformed note는 note id와 이유를 로그에 남기고 다음 note를 계속 처리한다.

실제 MySQL import 결과는 WORD 9,159, GRAMMAR 3,605, Meaning 10,617, Example 12,774이다. JLPT 관계는 N1 4,511, N2 3,531, N3 2,478, N4 1,125, N5 1,119이며 multi-level source item은 없었다. 모든 새 콘텐츠는 PENDING·비공개다. 기존 검토 이력이 있던 APPROVED 2건은 자동으로 변경하지 않았고, 과거 import의 null review status 18건만 PENDING으로 정규화했다.

품질 감사에서 source WORD의 빈 표기·읽기, blank grammar pattern·explanation, HTML markup 잔존, 인식 불가 pitch 값은 모두 0이었다. 모든 source vocab은 pitch raw value를 가졌고, level 없는 imported content와 multi-level 사례도 0이었다. 전체 PENDING 데이터는 공개 검색·`/today`·진도맵 denominator에서 제외되는 것이 정책상 정상이다.

실제 파일 smoke test와 importer fixture/parser test를 실행했고, 전체 Gradle test 결과는 48 tests, 0 failures, 0 errors, 1 conditional skip이다. 공개 전에는 APPROVED 2건의 검토 이력을 확인하고, 전체 PENDING 콘텐츠의 라이선스·출처 검토 및 대표 검색 query의 EXPLAIN 측정을 완료해야 한다.

## MySQL 대량 import 후 공개 검증

실제 MySQL에서 source note 재실행 후에도 WORD 9,159·GRAMMAR 3,605 수는 증가하지 않았다. source record는 vocab 9,159, grammar 3,605과 연결돼 있으며, 이전 실행에서 남아 있던 quiz/reference raw record는 콘텐츠로 노출하지 않는다.

공개 E2E를 위해 기존 APPROVED 2건은 유지하고, 아래 PENDING 8건만 검토 이력 `대량 데이터 실제 사용자 흐름 검증용 소수 승인`과 함께 명시적으로 승인했다.

- WORD: `jlpt-max-ci-b1cf5c26ca5090187081`, `jlpt-max-ci-456218743998d1f2e8ed` (N5), `jlpt-max-ci-c02021f93170c705ab47`, `jlpt-max-ci-f1b455c5ca49191e914a` (N4)
- GRAMMAR: `jlpt-max-grammar-1788408430326`, `jlpt-max-grammar-1788408430328` (N5), `jlpt-max-grammar-1788408431039`, `jlpt-max-grammar-1788408431041` (N4)

따라서 공개 콘텐츠는 WORD 8, GRAMMAR 5뿐이며, JLPT MAX import 원천의 나머지 12,754건은 PENDING·비공개로 유지한다.

MySQL `EXPLAIN ANALYZE`에서 공개 exact 표기/읽기/pattern 후보 query는 `content_items.idx_content_published_type`와 word/grammar의 content-item unique index를 사용했고 13 공개 row 기준 약 0.11ms였다. 한국어 의미 leading-wildcard query는 meaning의 word FK lookup을 사용했고 약 0.84ms였다. leading wildcard 특성상 의미 search_key의 B-tree range scan은 사용하지 않는다. 현재 공개 분모가 13건이므로 전체 PENDING 데이터를 공개한 것처럼 성능 결론을 내리지는 않는다. 인덱스 추가와 외부 검색 엔진 도입은 근거가 없어 하지 않았다.

## 신규 사용자 온보딩과 JLPT 진도맵

### 온보딩

새로 가입한 계정의 `LearnerProfile`은 `onboardingCompleted=false`로 생성된다. 로그인 뒤 홈과 오늘의 학습에 처음 들어오면 `/onboarding`으로 이동하며, 온보딩은 목표 JLPT, 실제 공개 Category, 하루 학습량을 짧게 설정하도록 구성되어 있다.

온보딩은 별도 설정 테이블을 만들지 않는다. 목표 JLPT와 분야는 기존 `LearnerStudyPreference`의 `LearningScope`에, 새 단어·문법 수와 일일 목표는 기존 사용자 설정에 저장한다. 현재 수준은 목표 범위를 추천하기 위한 화면 입력일 뿐 합격 레벨이나 별도 사용자 속성으로 저장하지 않는다. `잘 모르겠음`은 가장 낮은 제공 JLPT 범위부터 시작한다.

프리셋은 가볍게(새 단어 3, 새 문법 2, 목표 5), 보통(5, 5, 10), 집중(10, 5, 15)이며 직접 수치도 입력할 수 있다. 복습은 기존처럼 새 학습보다 우선한다. 온보딩 완료만으로 LearningProgress, StudyRecord, EXP, Streak는 생성되지 않는다.

기존 프로필은 새 nullable 완료 필드가 `null`이므로 이미 완료된 것으로 간주한다. 따라서 배포 전부터 있던 계정은 온보딩을 다시 하지 않아도 되고, 신규 계정만 완료 여부를 안전하게 유지한다.

`/onboarding/complete`에서는 저장된 플랜을 확인한 뒤 오늘의 학습 또는 설정으로 이동한다. 로그인 홈은 새 단어·문법 일일 플랜을 컴팩트하게 함께 표시한다.

### JLPT 진도맵

`/progress`는 공개(`published=true`) 콘텐츠만 대상으로 레벨별 WORD·GRAMMAR 상태 분포를 보여 준다. `LearningProgress`가 없으면 미학습이며, 있으면 `LEARNING`, `REVIEW`, `MASTERED`, `SUSPENDED`를 각각 분리한다. 기존 행의 null 상태는 마지막 결과를 바탕으로 LEARNING 또는 REVIEW로 호환 처리한다.

집계는 `ContentItem → Level`과 현재 사용자의 `LearningProgress`를 DB에서 left join한 aggregate query로 처리한다. 콘텐츠가 여러 Level에 속하더라도 해당 Level 내부에서는 `distinct item.id`로 한 번만 센다. 각 유형에서 미학습·학습 중·복습 중·숙달·보류의 합은 전체 공개 콘텐츠 수와 일치한다. 전체 콘텐츠나 전체 학습 기록을 메모리로 가져오지 않으며, 레벨별 반복 조회도 하지 않는다.

현재 학습 범위인 레벨은 진도맵에서 표시하지만 다른 JLPT 레벨도 열람할 수 있다. `계속 학습`은 기존 `/study?level=...&type=...` 자유 학습으로 연결하며, 버튼 클릭만으로 LearningProgress나 학습 범위를 바꾸지 않는다.

### 추가 API

- `GET /api/v1/onboarding`: 현재 온보딩 필요 여부, 선택지, 현재 플랜
- `GET /api/v1/onboarding/options`: 실제 JLPT Level·공개 Category·학습량 프리셋
- `POST /api/v1/onboarding/complete`: 기존 학습 범위와 일일 학습 설정으로 온보딩 완료
- `GET /api/v1/onboarding/plan`: 현재 사용자 학습 플랜
- `GET /api/v1/progress/jlpt`: JLPT 전체 진도맵
- `GET /api/v1/progress/jlpt/{level}`: 특정 JLPT Level 진도

온보딩·진도 API는 모두 인증된 현재 사용자만 사용하며 사용자 ID를 요청 파라미터로 받지 않는다.

## 학습 큐, 기록, 나만의 단어장

### 학습 큐

`StudyQueueEntry`는 사용자와 콘텐츠를 연결하는 별도 관계다. 북마크와 달리 앞으로 정규 SRS 학습에 넣고 싶은 콘텐츠를 뜻한다. 큐에 추가하거나 제거하는 것만으로는 LearningProgress, StudyRecord, EXP, 일일 목표, Streak가 바뀌지 않는다.

오늘 계획의 우선순위는 **복습 예정 → 큐의 미학습 콘텐츠 → 일반 미학습 콘텐츠**다. 큐의 미학습 콘텐츠는 단어·문법별 새 학습 상한 안에서 먼저 배치된다. 사용자가 직접 큐에 넣은 항목은 명시적 의도로 보고 일반 JLPT·Category 범위를 넘어도 새 학습 후보가 된다. 이미 LearningProgress가 있는 콘텐츠는 큐에 중복 추가하지 않으며, 큐에서 실제로 새 학습을 시작하면 큐 항목은 자동으로 제거된다.

### 오늘 리포트와 기록

`LearningHistoryService`는 HTTP session 값이 아니라 날짜 범위의 StudyRecord와 QuizAttempt를 사용한다. 오늘 완료 화면에는 새 단어·문법, 복습, 다시 보기, 정규 목표, 남은 복습, 오늘 획득 EXP, Streak, 내일 예정 복습을 표시한다. EXP는 NEW/REVIEW StudyRecord의 실제 결과로 계산하며 RETRAIN은 0 EXP다. Quiz는 별도 보조 활동과 EXP로 분리한다.

`/history`는 월별 범위만 조회해 compact activity calendar를 만들고, 선택 날짜의 NEW·REVIEW·RETRAIN·Quiz 요약과 카드 학습 목록을 제공한다. 과거 전체 StudyRecord를 메모리로 읽지 않는다.

### 나만의 단어장

`StudyCollection`과 `StudyCollectionItem`은 목적별 사용자 컬렉션이다. 단어와 문법을 함께 담을 수 있고, 하나의 콘텐츠는 여러 컬렉션에 포함될 수 있다. 컬렉션은 북마크나 학습 큐와 별개다.

컬렉션 학습은 자유 재학습 흐름이다. 기존 SRS와 Streak에는 반영하지만 EXP와 정규 일일 목표에는 반영하지 않는다. 이미 학습한 콘텐츠의 LearningProgress를 새로 만들지 않고, 처음 보는 콘텐츠도 기존 LearningProgress 규칙으로 시작한다.

### 추가 API

- `GET/POST/DELETE /api/v1/study/queue`, `/{slug}`: 학습 큐 조회·추가·제거
- `GET/POST/DELETE /api/v1/collections`: 컬렉션 조회·생성·삭제
- `GET/POST /api/v1/collections/{id}`: 컬렉션 상세·이름 변경
- `POST/DELETE /api/v1/collections/{id}/items/{slug}`: 콘텐츠 추가·제거
- `GET /api/v1/collections/{id}/study`: 컬렉션 자유 학습 카드
- `GET /api/v1/history/month?month=YYYY-MM`: 월별 활동
- `GET /api/v1/history/day?date=YYYY-MM-DD`: 날짜별 리포트

모든 큐·컬렉션·기록 API는 인증이 필요하며 소유자 조회 조건을 사용한다. 다른 사용자의 항목은 조회·수정할 수 없다.

## 문법 학습 보강 구조 (2026-09-11)

JLPT MAX에서 가져온 `Grammar` 행은 원본 콘텐츠로 유지한다. 패턴, 설명, 실제로 존재하는 접속 정보, 예문, 읽기, 번역, 레벨을 보존하며, `UnitID` 같은 내부 APKG 값으로 비어 있는 접속 정보를 추론하지 않는다.

검토용 보강 정보는 원본과 별도 구조에 저장하며, 처음에는 모두 `PENDING`·비공개 상태다.

- `GrammarEnrichment`: 뉘앙스, 사용 상황, 접속 보충, 자주 하는 실수, 학습 메모, 출처, 검토 상태
- `GrammarRelation`: 정규화한 문법 쌍과 `SIMILAR`·`CONTRAST`·`CONFUSABLE` 관계. 작은 Grammar ID를 먼저 저장하여 A/B와 B/A 중복을 막고, 자기 자신 관계는 거부한다.
- `GrammarComparison`: 관계별 핵심 차이, 사용 차이, 자주 헷갈리는 점, 출처를 담는 검토용 비교 설명
- `GrammarConfirmationQuestion`·선택지·`GrammarConfirmationAttempt`: 문맥 빈칸·의미 선택·관계 구분 확인 문제와 별도 풀이 기록

공개 화면과 API에는 `APPROVED`이면서 `published=true`인 curated 데이터만 노출한다. 대기 중인 보강·관계·비교·문제는 문법 상세와 API에서 보이지 않는다. `GrammarCurationService`가 향후 검토 화면이나 검증된 fixture import의 등록 경계다.

### 문법 학습 경험

- 문법 상세와 정규 학습 카드는 원본 설명·접속·예문을 먼저 보여준다.
- 승인된 보강 정보는 내용이 있는 항목만 렌더링하며 빈 섹션을 만들지 않는다.
- 승인된 관계가 있으면 관련 문법과 관계 유형을 보여주고, 별도 승인된 비교 설명이 있을 때만 비교 화면으로 연결한다.
- 확인 문제는 선택형 보조 활동이다. `LearningService.answer`를 호출하지 않으므로 `StudyRecord`, `LearningProgress`, SRS, EXP, 일일 목표, Streak를 바꾸지 않는다. 오답은 문법 약점 신호로만 남긴다.

### 문법 API

- `GET /api/v1/grammars/{slug}/learning`: 승인된 보강, 관련 문법, 확인 문제 가능 여부
- `GET /api/v1/grammars/{slug}/compare/{otherSlug}`: 승인된 비교 설명
- `GET /api/v1/grammars/{slug}/confirmation`: 승인된 확인 문제와 안전한 선택지 DTO
- `POST /api/v1/grammars/confirmations/{id}/answer`: 인증된 사용자 풀이 제출
- `GET /api/v1/grammars/weaknesses`: 인증된 사용자의 보조 문법 약점 목록

웹 경로는 `/grammars/{slug}/compare/{otherSlug}`, `/grammars/{slug}/confirm`, `POST /grammars/confirmations/{id}/answer`다.

### 검증과 남은 작업

보강·관계·비교의 공개 상태, 정규화한 관계 중복 방지, 확인 문제 풀이 저장과 EXP/SRS/정규 학습 기록 격리를 Service·MockMvc 테스트로 검증했다. 실제 JLPT MAX 원본 행은 수정하거나 재공개하지 않았다.

대규모 공개 전에는 사람 검토를 거친 설명·문제만 각 curated 레코드 단위로 `APPROVED`·공개 처리해야 한다. APKG 원본과 curated 출처는 계속 분리한다.

## N5 문법 검토용 curated 초안 (2026-09-11)

`grammar-curation` 프로필의 `N5GrammarCurationSeed`는 기존 `GrammarCurationService`만 사용한다. imported `Grammar` 원본, 공개 상태, 사용자 기록, SRS, 학습 통계는 변경하지 않는다.

- 실제 APKG 감사 결과 N5 Grammar note는 340개다. 현재 공개된 N5 원본 행은 `jlpt-max-grammar-1788408430326`(`あの`)와 `jlpt-max-grammar-1788408430328`(`あまり飲みません`)뿐이며, 나머지 선택 원본은 PENDING·비공개 상태를 유지한다.
- MySQL에는 N5 보강 초안 30개, 정규화 관계 12개, 비교 초안 12개, 문맥 빈칸 확인 문제 초안 30개를 등록했다. 총 84개 curated 레코드는 모두 `PENDING`·비공개다.
- 범위는 이유(`から`), 가능(`～ことができる`), 희망·의도(`～たい`·`～つもり`), 순서(`～たあとで`·`～てから`·`～前に`), 요청·금지·허가, 부정 연결, 이동 목적, 선택·변화, 비교, 제안이다. 관계는 텍스트 유사도가 아니라 명시적으로 검토한 `SIMILAR`·`CONTRAST`·`CONFUSABLE` 쌍만 등록한다.
- 각 curated 초안에는 JLPT Sensei N5 문법 색인(`https://jlptsensei.com/jlpt-n5-grammar-list/`)과 Tae Kim 문법 가이드(`https://www.guidetojapanese.org/grammar_guide.pdf`)를 검토 출처로 저장한다. 이는 JLPT MAX 원본 필드가 아니다.
- `grammar-curation`을 다시 실행해도 행이 중복되지 않는다. 문제는 `(grammar, sourceRef)`를 안정적인 upsert 키로 사용하고 기존 문제의 선택지만 갱신한다.

MySQL 8.0.39에서 seed 프로필을 두 번 실행해 재실행 안전성을 확인했다. 익명 HTTP 요청으로 공개 N5 문법 상세를 확인한 결과 PENDING 보강·비교·확인 문제 텍스트는 노출되지 않았다. 확인 문제 격리와 문제 upsert 테스트는 `StudyRecord`, `LearningProgress`, SRS, EXP, 일일 목표, Streak가 바뀌지 않음을 확인한다.

공개 사용 전에는 각 원본 문법의 라이선스 승인과 curated 초안의 사람 검토가 필요하다. 해당 curated 레코드만 개별적으로 `APPROVED`·공개 처리하며, 현재 MySQL의 초안은 데스크톱·모바일 공개 경로에서 사용할 수 없다.

## 오늘의 학습 세션 (2026-09-12)

`TodayStudySession`과 `TodayStudySessionItem`은 학습자와 한국 날짜별로 하나의 정규 학습 계획을 DB에 저장한다. 세션 생성 시 기존 `/today` 계획의 우선순위(복습 예정 → 학습 큐의 미학습 콘텐츠 → 일반 미학습 콘텐츠)와 단어·문법 일일 상한을 그대로 스냅샷으로 저장한다. 생성 후에는 새 복습이 도래하거나 설정이 바뀌어도 카드 순서를 재구성하지 않는다.

`GET /today`는 현재 미완료 카드 한 장과 전체 진행 수·남은 수·복습/새 단어/새 문법 구성을 보여준다. 브라우저를 닫거나 다른 화면으로 이동해도 같은 날짜의 저장된 세션과 다음 미완료 카드로 이어진다. 완료된 세션 뒤 새로 도래한 복습은 기존 자유 복습 경로(`/study?reviewOnly=true`)에서 처리할 수 있다.

완료 요청은 세션 행의 비관적 잠금, 세션 항목 완료 상태, 기존 `StudyRecord`의 세션 키 기반 중복 방지를 함께 사용한다. 새로고침·뒤로가기·빠른 재전송은 완료된 카드를 다시 기록하지 않으며, StudyRecord·SRS·EXP·일일 목표·Streak가 중복 반영되지 않는다. 문법 confirmation은 기존처럼 별도 보조 활동이라 정규 카드 완료를 추가 기록하지 않는다.

마지막 카드를 마치면 `LearningHistoryService`의 실제 날짜별 기록으로 새 단어·새 문법·복습·다시 보기·EXP·목표·Streak·남은 backlog·다음 날 복습을 표시한다. HTTP session 값에 의존하지 않으므로 새 접속에서도 결과가 유지된다.

## 오답·약점 노트 2.0 (2026-09-12)

`/weaknesses`는 단순 최근 오답 목록이 아니라 최근 30일의 실제 학습 기록을 근거로 약점을 정리한다. 정규 카드 학습과 RETRAIN의 `StudyRecord`, 그리고 별도 보조 활동인 `GrammarConfirmationAttempt`를 각각 DB aggregate query로 집계한다. 전체 StudyRecord를 메모리로 읽지 않으며, 항목별 후보 집계는 최대 250건, 화면 섹션은 최대 50건으로 제한한다.

- **최근 틀린 항목**: 마지막 정답보다 마지막 오답이 같거나 더 최근인 콘텐츠
- **자주 틀린 항목**: 최근 30일에 정규 SRS 학습 또는 문법 확인 문제에서 두 번 이상 틀린 콘텐츠
- **연속으로 틀린 항목**: 가장 최근 정답 뒤에 실제 오답이 두 번 이상 이어진 콘텐츠
- **개선된 약점**: 반복 오답 뒤에 정규 SRS 연속 정답 2회 이상 또는 문법 확인 정답 2회 이상이 확인된 콘텐츠

각 항목은 WORD/GRAMMAR, SRS 오답 수, 문법 확인 문제 오답 수, 연속 오답 수, 마지막 오답 시각과 실제 기록으로 만든 이유를 표시한다. 문법 confirmation은 약점 근거만 남기며 기존 정책대로 `LearningProgress`, SRS, EXP, 정규 일일 목표, Streak를 바꾸지 않는다.

`WeaknessReviewSession`과 `WeaknessReviewSessionItem`은 현재 약점 후보 중 최대 10개를 순서대로 스냅샷으로 저장한다. `/weaknesses/session`에서 중단 후 이어할 수 있고, 세션 행 잠금·현재 카드 순서 검증·기존 `StudyRecord(learner, sessionKey, content)` unique key를 함께 사용해 새로고침·뒤로가기·중복 POST를 한 번의 RETRAIN으로 제한한다. 완료는 기존 `LearningService.answer(..., retraining=true)`만 사용하므로 SRS와 Streak에는 반영되지만 EXP와 정규 일일 목표에는 반영되지 않는다.

기존 `/study/relearn/weaknesses`와 `GET /api/v1/study/relearn/WEAKNESSES`도 같은 약점 후보를 사용하도록 연결했다. Android용으로는 `GET /api/v1/weaknesses`, `GET /api/v1/weaknesses/session`, `POST /api/v1/weaknesses/session/start`, `POST /api/v1/weaknesses/session/{slug}/complete`를 제공한다. 모든 개인 약점·세션 조회는 인증된 현재 사용자 기준이라 다른 사용자 기록을 조회하거나 완료할 수 없다.

`WeaknessNoteServiceTest`는 최근/빈번/연속/개선 상태, 문법 confirmation 오답 격리, 사용자 분리, 집중 재학습의 이어하기·중복 제출·EXP/정규 목표 비증가를 검증한다. `RedesignedViewsTest`는 웹 약점 노트, REST 응답, 집중 재학습 화면과 완료 흐름을 검증한다.

## 주간 학습 리포트 (2026-09-12)

`/report/weekly`와 `GET /api/v1/reports/weekly`는 한국 시간 기준 오늘을 포함한 최근 7일과 직전 7일을 비교한다. `StudyRecord`, `QuizAttempt`, `GrammarConfirmationAttempt`를 각각 날짜 범위 aggregate query로 집계하며, 전체 학습 기록을 메모리로 읽지 않는다. 일별 흐름은 7일 고정 범위에 대해 aggregate query만 실행한다.

- 정규 학습은 `NEW` 단어·문법, `REVIEW`, 정답률, 학습 EXP를 보여준다. 정답률과 학습 EXP에는 `RETRAIN`을 포함하지 않는다.
- `RETRAIN`, Quiz, 문법 confirmation은 보조 활동으로 별도 표시한다. Quiz EXP는 저장된 `QuizAttempt.earnedExperience`를 사용하고, confirmation은 EXP·SRS를 변경하지 않는 기존 정책을 유지한다.
- JLPT 진도 변화는 과거 LearningProgress 상태를 추정하지 않고, 기간 내 실제 `NEW` StudyRecord가 생성된 고유 콘텐츠 수로 정의한다. 따라서 `N5 단어 +3개`는 이번 주 N5 단어를 처음 시작한 콘텐츠가 3개라는 뜻이다.
- 이번 주 약점·개선 항목은 약점 노트 2.0의 같은 근거 규칙을 7일 범위에 적용한다. 단어와 문법을 분리하고 문법 confirmation 오답도 근거로 보이되 정규 SRS와 섞지 않는다.

다음 행동은 임의 점수나 AI 판단 없이 현재 복습 backlog, 이번 주 오답 근거, 학습일 수, 새 콘텐츠 시작 여부로 결정한다. 복습 backlog가 있으면 복습 보기, 실제 약점이 있으면 약점 노트, 학습일이 3일 미만이면 오늘의 학습을 우선 제안한다.

`WeeklyLearningReportServiceTest`는 7일 시작 경계 포함, 직전 주 비교, RETRAIN·Quiz·confirmation 분리, JLPT 신규 시작 집계, 빈 데이터와 사용자 격리를 검증한다. 화면/API 응답은 `RedesignedViewsTest`에 포함했다.

## 기존 사용자 설정 조회 호환 수정 (2026-09-12)

기존 사용자에게 `LearnerStudyPreference` 행이 없을 수 있다. 읽기 전용 홈·온보딩 상태 조회에서 기본 설정을 저장하려 하면 MySQL read-only 연결에서 실패할 수 있어, 이제 조회 경로는 저장하지 않은 기본값을 반환한다. 온보딩 완료와 설정 변경 같은 쓰기 요청에서만 설정 행을 생성한다. `LearningServiceTest`로 이 호환 경로를 검증한다.

Android도 같은 규칙을 쓸 수 있도록 `GET /api/v1/study/today/session`, `POST /api/v1/study/today/session/{slug}/complete`를 추가했다. 기존 `GET /api/v1/study/today`는 계획 미리보기 API로 유지한다.

Service 및 MockMvc E2E 테스트로 세션 시작, 첫 카드 완료, 설정 변경 뒤 이어하기, 같은 완료 요청 재전송, 전체 완료, 날짜별 리포트 반영과 웹/API 세션 조회를 검증했다. `./gradlew.bat test`는 56개 통과, 실패·오류 0건으로 완료됐다. 실제 APKG 경로가 있을 때만 실행하는 기존 smoke test 1개는 조건부 제외 상태다.

## 승인 디자인의 실제 애플리케이션 적용 (2026-09-12)

`design-prototype/`을 비교 기준으로 유지하면서 실제 Thymeleaf 화면에 Design Guide v2를 적용했다. 공통 데스크톱 레일, 상단 검색, 모바일 5탭 내비게이션과 typography·spacing·button·input·tag·progress 규칙은 `app-v2.css`에서 공유한다. 기존 `app.css`는 호환 기반으로 남겨 도메인 기능 화면을 삭제하지 않았다.

- Home은 실제 오늘 계획, 목표, EXP, 성장 단계, Streak, 최근 기록과 기존 Haru 상태·asset 경로를 연결한다. Haru 영역은 향후 상태별 프레임 애니메이션을 넣을 수 있는 data attribute 구조를 유지하며 JLPT 진도와 캐릭터 성장을 분리했다.
- `/today`는 DB snapshot/resume 세션의 현재 카드와 실제 진행량을 집중형 화면에 표시한다. 완료 POST, SRS, 중복 방지, EXP·목표·Streak 규칙은 기존 Service를 그대로 사용하고 완료 화면은 `LearningHistoryService`의 실제 기록을 표시한다.
- `/dictionary`를 공개 독립 사전 경로로 추가했다. 기존 DB 검색·ranking·pagination Service를 재사용하며 모바일 필터는 결과를 가리지 않도록 기본 접힘, 데스크톱은 펼침으로 동작한다. 기존 `/?keyword=...` 요청도 같은 사전 화면으로 연결한다.
- Word/Grammar 상세는 다중 Meaning·Example, 피치 악센트, 문법 보강·관계·confirmation을 실제 DTO가 제공할 때만 렌더링한다. 학습 큐, 북마크, 컬렉션, 현재 학습 상태와 오늘 학습 진입을 기존 POST/API에 연결했다.
- Review, 약점 집중 재학습, JLPT 진도, 주간 리포트, 기록, 통계, 설정, 온보딩, 저장함·학습 큐·컬렉션은 승인된 IA와 공통 디자인 언어로 정리했다. `/my-learning`은 이 기능들의 허브이며 저장 개수는 전체 엔티티 로딩 대신 DB count query로 조회한다.
- 데스크톱 레일과 모바일 내 학습 화면에 로그아웃을 제공한다. 공개 탐색은 익명 사용자가 이용하고 개인 화면은 기존 Spring Security 정책을 유지한다.

실제 MySQL에서 별도 UI 검수 계정으로 회원 가입, 온보딩, 검색, 상세, 큐·북마크·컬렉션 저장, 오늘 학습 5장, 오답 1건, 완료 리포트, Home 반영, Review, 약점, 진도, 주간 리포트, 기록, 설정, 로그아웃·재로그인을 확인했다. 1440×1000 데스크톱과 390×844 모바일에서 주요 화면의 horizontal overflow와 Whitelabel 오류가 없었고, 재로그인 시 온보딩이 반복되지 않았다. 검수 과정에서 인증 화면 너비가 레일만큼 넘치던 flex margin 문제, 모바일 사전 필터가 결과를 밀어내던 문제, 자유 학습 action이 Today 고정 dock 스타일을 상속하던 문제, UI에서 로그아웃 진입점이 빠진 회귀를 수정했다.

전체 `./gradlew.bat test` 결과는 **63개 중 62개 통과, 실패·오류 0개, 실제 APKG 경로 조건 smoke test 1개 제외**다.

## Haru young stage 최소 모션과 Blink (2026-09-13)

기존 `young.png`를 변경하지 않고 `idle`의 미세 호흡·정착, `study`, `happy`, `goal-complete`를 하체 고정 CSS motion으로 연결했다. 이는 한 장의 poster를 이용한 최소 반응이며 새 표정이나 자세 frame으로 취급하지 않는다. Home의 학습 시작 링크는 `study` 반응 후 이동하며, Today는 완료 POST 뒤 서버가 전달하는 `study`·`happy`·`goal-complete` 상태를 표시한다. 별도 전환 asset이 필요한 `look-around`와 `growth`는 idle poster fallback을 유지한다.

Blink는 manifest v2와 범용 frame animator에 실제 asset sequence로 연결했다. 재생 순서는 `young.png` → `young-blink-01.png` → `young-blink-02.png` → `young-blink-03.png` → `young.png`이며 hold는 각각 90 / 70 / 110 / 70 / 120ms, 전체 460ms다. 눈꺼풀 전환 구간은 기존 제작 timing인 70 / 110 / 70ms를 사용한다.

idle ambient는 8~16초 범위에서 매번 무작위로 다음 실행 시점을 정하고 breathe 4, Blink 2, settle 1의 가중치로 동작한다. 공유 timer 하나만 사용하며 Blink가 끝나면 idle poster와 ambient lifecycle로 복귀한다. `study`, `happy`, `goal-complete` 등 non-idle 상태, 숨겨진 document, `prefers-reduced-motion: reduce`에서는 Blink를 실행하지 않는다. 상태 변경이나 reduced-motion 전환 중에는 진행 중인 frame 대기를 취소하고 늦게 끝난 이미지 요청이 현재 poster를 덮지 못하게 run id로 격리한다. reduced-motion에서는 `young.png`만 표시하고 Blink frame을 요청하지 않는다. CSS 크기와 template은 변경하지 않았다.

프레임 제작 계약과 asset 상태는 [`haru-animation-assets.md`](haru-animation-assets.md)에 기록했다. 브라우저 lifecycle 테스트에서 open → half → closed → half → open 순서, duration, idle 복귀, timer 중복 방지, non-idle 충돌 방지, visibility 변경, reduced-motion frame 미요청, 404 부재와 desktop/mobile layout 크기 유지를 확인했다.

## STS 로그인 파라미터 메타데이터 설정 (2026-09-13)

STS에서 실행한 서버가 controller의 이름 없는 `String` 인자를 해석하지 못해 로그인 요청에서 `Name for argument ... not specified` 예외가 발생할 수 있었다. Gradle 빌드는 이미 Java parameter metadata를 생성하지만 STS의 Eclipse compiler 설정에는 같은 옵션이 없었던 것이 원인이었다. `.settings/org.eclipse.jdt.core.prefs`를 저장소 설정으로 포함하고 `org.eclipse.jdt.core.compiler.codegen.methodParameters=generate`와 Java 17 compiler 수준을 명시했다. controller나 로그인 정책, backend 동작은 변경하지 않았다.

## UI v2 전체 polish (2026-09-13)

기존 Design Guide v2의 색상, typography, 정보 구조와 화면 기능을 유지하면서 `app-v2.css`의 간격과 모서리 체계를 정리했다. 공통 token은 작은 요소 4px, 보조 요소 6px, input·button 8px, 주요 panel 10px이며 pill은 tag·chip·progress처럼 작은 요소에만 사용한다. 페이지 가로·세로 여백과 section 간격도 공통 token으로 묶었다. 새 card를 추가하지 않고 평면 section과 기존 정보 밀도를 유지했다.

- 로그인·온보딩, Home, 상세, Settings, 저장함, 내 학습, Progress, Weekly Report, History에서 과한 top padding, panel padding과 반복 section 간격을 줄였다.
- 긴 page heading과 action이 모바일에서 자연스럽게 줄을 바꾸도록 했고, 사전 filter 영역과 filter sheet의 radius·overflow를 정리했다.
- 모바일 하단 navigation 높이에 safe-area를 더하고 body reserve와 같은 값을 사용해 마지막 콘텐츠가 가려지지 않게 했다.
- 상세 화면의 sticky action panel과 lesson 간격, 빈 검색 결과 공간, Home 하단 간격을 줄였다.

실제 Spring Boot 앱을 Edge에서 1440×1000과 390×844로 확인했다. 로그인·회원가입, 온보딩, Home, 오늘 학습, 사전과 검색 결과, 단어·문법 상세, Review, 약점과 집중 복습, 내 학습, Bookmark·Queue·Collection, Progress, Weekly Report, History, Settings를 점검했고, 저장 데이터가 있는 Bookmark·Queue·Collection·약점 화면도 별도 확인했다. DOM·state·network·frame lifecycle 기준으로 JavaScript 오류, 의미 있는 clipping, horizontal overflow, 하단 navigation 겹침과 missing asset/404가 없었다.

최신 전체 `./gradlew.bat test` 결과는 **64개 중 63개 통과, 실패·오류 0개, 실제 APKG 경로 조건 smoke test 1개 제외**다. `git diff --check`도 통과했다.

## 계정 운영 기능 (2026-09-13)

기존 로그인 ID 기반 Spring Security 로그인·로그아웃과 BCrypt 정책을 유지하면서 이메일 인증, 비밀번호 재설정, 표시 이름 변경을 추가했다. 이메일 인증 여부는 `UserAccount.emailVerified`에 nullable Boolean으로 저장한다. 새 이메일 가입자는 미인증으로 시작하지만 로그인은 막지 않으며, 컬럼 추가 전에 만들어진 기존 사용자의 null 값은 인증 완료로 해석해 기존 계정을 잠그지 않는다.

이메일 인증과 비밀번호 재설정은 각각 별도 token entity를 사용한다. 원문 token은 `SecureRandom`으로 만든 256-bit URL-safe 값이며 DB에는 SHA-256 hash만 저장한다. 인증 링크는 24시간, 재설정 링크는 30분 동안 유효하고 한 번 사용하면 다시 사용할 수 없다. 새 token을 발급할 때 같은 사용자의 이전 미사용 token을 무효화하며, 60초 재요청 제한과 DB 잠금으로 중복 요청을 제어한다. 이메일 인증은 이미 완료된 계정에 반복 적용해도 상태를 바꾸지 않는다.

비밀번호 재설정 요청은 이메일 존재 여부와 관계없이 같은 202 응답과 웹 메시지를 반환한다. 완료 시 회원가입과 같은 8~72자 정책을 적용하고 BCrypt hash만 저장한다. 비밀번호, hash, 원문 token은 API DTO에 포함하지 않으며 민감 요청 DTO의 문자열 표현도 redacted 처리했다.

표시 이름 변경은 인증된 현재 사용자만 수행할 수 있고 요청에서 사용자 ID를 받지 않는다. `UserAccount`와 연결된 `LearnerProfile`을 같은 트랜잭션에서 잠그고 함께 갱신하므로 Settings, Home과 공통 navigation에 즉시 반영된다.

주요 REST API는 다음과 같다.

- `GET /api/v1/account`: 현재 계정의 로그인 ID, 이메일, 표시 이름, 이메일 인증 상태, 가입일
- `POST /api/v1/account/display-name`: 인증된 현재 사용자의 표시 이름 변경
- `POST /api/v1/account/email-verification/resend`: 인증된 현재 사용자의 인증 메일 재발송
- `POST /api/v1/account/email-verification/request`, `/confirm`: 공개 인증 요청과 token 확인
- `POST /api/v1/account/password-reset/request`, `/complete`: 계정 존재 여부를 숨기는 재설정 요청과 완료

메일 전송은 `AccountMailSender`로 분리했다. 기본 development 모드는 외부 SMTP 없이 메모리에만 보관하고, 운영에서는 `ACCOUNT_MAIL_MODE=smtp`, `ACCOUNT_PUBLIC_BASE_URL`, `ACCOUNT_MAIL_FROM`, `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`로 JavaMailSender를 사용한다. 비밀값은 `secrets.yml` 또는 환경 변수로만 주입한다. 개발 token 조회 Controller는 `japanese.account.dev-mailbox-enabled=true`를 명시한 실행에서만 생성된다.

실제 MySQL과 headless Edge에서 회원가입 → 이메일 인증 → 로그인 → Settings 표시 이름 변경 → 로그아웃 → 비밀번호 재설정 요청 → 새 비밀번호 설정 → 기존 비밀번호 거부 → 새 비밀번호 로그인을 확인했다. 신규 계정 화면과 Settings를 1440×1000 및 390×844에서 검사했고 horizontal overflow, clipping, mobile navigation 겹침, JavaScript 오류와 HTTP 4xx/5xx가 없었다.

최신 전체 `./gradlew.bat test` 결과는 **76개 중 75개 통과, 실패·오류 0개, 실제 APKG 경로 조건 smoke test 1개 제외**다. `git diff --check`도 통과했다.

## Daily Mission · Smart Next Action · 학습 리마인더 (2026-09-13)

Daily Mission은 별도 mission entity나 증가형 카운터를 저장하지 않는 조회 모델이다. 오늘 세션이 생성된 뒤에는 `TodayStudySession`과 `TodayStudySessionItem`의 고정 snapshot 및 완료 상태로 목표와 진행도를 계산한다. 세션 생성 전에는 Asia/Seoul의 오늘 시작 시각 이후 생성된 정규 `StudyRecord`와 `LearningService.todayPlan()`의 남은 계획을 합쳐 복습·새 단어·새 문법 목표를 만든다. 따라서 설정 변경이나 새로고침으로 시작된 세션의 목표가 바뀌지 않고, 기존 세션 항목 잠금과 StudyRecord 중복 키가 같은 완료 POST의 중복 반영을 막는다.

RETRAIN은 mission과 일일 목표 EXP에서 제외하고 Quiz와 Grammar Confirmation도 각각 기존 보조 활동 정책을 유지한다. Daily Mission에는 별도 EXP나 Streak 규칙이 없으며 기존 StudyRecord 기반 EXP·SRS와 기존 Streak 계산을 변경하지 않는다.

`LearningGuidanceService`는 Home과 REST API가 공유하는 결정론적 상태를 만든다. 다음 행동은 진행 중 Today 세션 → 오늘 처리할 due review → 아직 끝나지 않은 오늘 계획 → 실제 오답 근거가 있는 약점 → 오늘 완료 → 기존 데이터 호환용 선택 학습 → 학습 콘텐츠 탐색 순서다. Home은 기존 오늘 패널 안에 가장 중요한 행동 하나, 리마인더 하나, 세 유형의 진행도만 통합해 별도 dashboard card를 늘리지 않았다. Today에는 현재 학습을 방해하지 않는 한 줄 진행 정보만 추가했다. 전체 mission 완료 시 기존 Haru `goal-complete` 상태를 재사용하며 asset과 animation 로직은 변경하지 않았다.

`LearningReminder`는 오늘 학습 필요 여부, due review 여부, mission 완료, 충분한 정규 학습 여부, 현재 Streak, 마지막 학습 날짜, 다음 review 시각을 함께 제공한다. 웹 메시지는 활성화된 사용자의 선호 시간 이후에만 미시작·복습·진행 중 상태 중 하나를 표시하며, 완료 상태는 즉시 반영한다. `LearnerStudyPreference`의 nullable `reminderEnabled`와 `reminderTime`만 새로 저장하고 기존/null 사용자는 활성·19:00으로 해석한다. 시간대 판단은 `LearningTime`에 모았으며 현재 서비스 기준은 `Asia/Seoul`이다.

Android 등 이후 클라이언트는 Controller 계산을 복제하지 않고 다음 API와 같은 Service 규칙을 재사용할 수 있다.

- `GET /api/v1/home/learning-status`: 날짜, mission, 다음 행동, 리마인더 상태를 한 번에 반환
- `POST /api/v1/reminders/preferences`: 인증된 현재 사용자의 활성 여부와 선호 시간 변경

두 API는 인증된 현재 계정만 사용하며 사용자 ID를 요청으로 받지 않는다. CSRF와 기존 Spring Security 정책도 유지한다.

서비스 테스트는 학습 시작 전·부분 진행·완료, snapshot 목표 유지, 새로고침과 중복 완료, 복습 우선, 새 단어·문법 진행, 약점 추천, RETRAIN·Quiz 격리, 기존 Grammar Confirmation 격리 정책, 리마인더 비활성·선호 시간·기본값, Asia/Seoul 자정 경계와 사용자 격리·anonymous·CSRF를 검증한다. 최신 전체 `./gradlew.bat test` 결과는 **86개 중 85개 통과, 실패·오류 0개, 실제 APKG 경로 조건 smoke test 1개 제외**다.

실제 MySQL 8.0.39와 headless Chrome에서 새 계정 회원가입·로그인·온보딩 후 2개 mission을 0/2 → 1/2 → 2/2로 진행했다. Home 복귀와 Today 재개에서 snapshot과 유형별 진행도가 유지됐고 완료 후 Home의 `goal-complete`, History, Weekly Report가 정상 반영됐다. 1440×1000 및 390×844에서 Home·Today·Settings·History·Weekly Report의 horizontal overflow, clipping, 모바일 navigation 겹침, JavaScript 오류, HTTP 4xx/5xx와 missing asset이 없었다.

현재 리마인더는 웹 Home의 in-app 안내와 향후 push 판단용 서버 상태까지만 제공한다. 실제 Android Push Provider, 백그라운드 발송 scheduler와 사용자별 timezone은 이번 범위에 포함하지 않았으며, timezone 접근 지점은 이후 확장할 수 있도록 한 Service에 모았다.

## Quiz 2.0 / 문제풀이 경험 고도화 (2026-09-13)

기존 단일 문제 Quiz와 별도로, 중단 후 이어하기와 중복 제출 방지를 지원하는 빠른 Quiz 세션을 추가했다. `QuizSession`은 인증된 학습자, UUID 공개 식별자, 상태, 문제 수, 정답 수, Quiz EXP를 저장하고 `QuizSessionItem`은 순서, 콘텐츠, 문제 유형, prompt·선택지·정답·설명 snapshot과 답변 결과를 저장한다. 진행 중인 빠른 Quiz는 다시 시작할 때 같은 세션으로 복귀하며, 완료 결과도 재조회할 수 있다.

문제는 published 콘텐츠와 사용자의 LearningScope만 사용해 session key 기반으로 결정론적으로 만든다. 1차 버전은 단어의 일본어→뜻, 뜻→일본어, 읽기 선택, 읽기 직접 입력과 문법의 패턴→의미, 의미→패턴, 문맥 선택을 지원한다. 선택지는 실제 다른 콘텐츠 값만 사용하고 정규화 중복을 제거하며, 충분한 선택지가 없으면 해당 유형을 건너뛰거나 단어 읽기 직접 입력으로 전환한다. 문법 confirmation 데이터는 published이면서 승인된 항목만 재사용한다. 읽기 직접 입력은 NFKC, 앞뒤/반복 공백, 가타카나→히라가나만 정규화한다.

세션 답안 POST는 소유자와 현재 문제를 확인하고 세션 row를 잠근다. 이미 처리한 같은 item 요청은 저장된 feedback을 돌려주므로 QuizAttempt와 EXP가 다시 증가하지 않는다. 문제 조회 DTO에는 정답과 설명을 포함하지 않으며, 기존 Quiz 상세 DTO와 화면에서도 제출 전 정답 노출을 제거했다. UUID와 현재 인증 사용자를 함께 조회해 다른 사용자의 세션 접근을 차단하고 기존 CSRF 정책을 유지한다.

Quiz 2.0은 기존 Quiz의 정답 10 EXP·오답 2 EXP와 QuizAttempt/Weekly Report 분류를 재사용한다. StudyRecord, LearningProgress, SRS, TodayStudySession, Daily Mission과 Daily Goal은 변경하지 않으며 Quiz 2.0 답안은 Streak에도 포함하지 않는다. 기존 단일 문제 Quiz의 동작과 Streak 호환성은 유지했다. Quiz 오답은 기존 Weakness 집계 근거에 새로 섞지 않았다.

REST API는 `POST /api/v1/quizzes`, `GET /api/v1/quizzes/{sessionId}`, `POST /api/v1/quizzes/{sessionId}/answers`, `GET /api/v1/quizzes/{sessionId}/result`, `POST /api/v1/quizzes/{sessionId}/restart`를 제공하며 웹 Controller도 같은 Service를 사용한다. 웹은 한 화면 한 문제, 명시적 선택/제출/feedback/다음 CTA, 직접 입력 Enter 제출, 결과 요약과 오답 콘텐츠 링크를 제공한다.

자동화 테스트는 문제 유형과 결정론, 정규화, 선택지 중복 제거와 fallback, unpublished·미승인 제외, resume, 정답/오답 집계, 중복 POST, 완료와 restart, 사용자 격리, CSRF/anonymous 정책, 정답 key 미노출, StudyRecord·SRS·Mission·Streak 격리를 검증한다. 실제 MySQL 8.0.39와 headless Chrome에서 회원가입·로그인·빠른 Quiz 5문제·정답/오답·직접 입력·문법·화면 이탈 후 resume·동일 답안 재전송·결과·History·Weekly Report·Home을 확인했다. 1440×1000과 390×844에서 Quiz 문제/결과 및 회귀 화면에 horizontal overflow, clipping, navigation overlap, JavaScript 오류가 없었다.

현재 1차 버전은 빠른 Quiz만 제공한다. Today 기반, 약점 기반, JLPT 선택 모드와 문맥 데이터가 부족한 단어 문제는 후속 범위다. 세션 간 반복 학습의 EXP 제한은 기존 Quiz 정책에 없는 전역 cooldown을 임의로 만들지 않고 유지했으며, 필요하면 별도 운영 정책으로 정해야 한다. 브라우저 검증 중 기존 작업 트리에서 삭제된 Haru `young.png` 요청 404가 확인됐으며 Quiz 코드와 무관해 이번 작업에서는 이미지 파일을 수정하지 않았다.
## Haru 성장 시스템 V2 (2026-09-13)

Haru의 성장을 누적 EXP와 새 Stage 1~4 표현에 연결했다. 장식이나 JLPT 레벨에 따른 진화 대신, 서툰 어린 친구가 몸의 균형을 잡고 함께 걷는 학습 파트너가 되는 방향이다. 기존 `CharacterGrowthStage`의 YOUNG/APPRENTICE/CONFIDENT/RELIABLE 및 API semantic key는 유지하고 UI/manifest의 stage-1~4와 분리했다. 사용자 Level은 기존대로 `EXP / 100 + 1`이며 성장 단계와 독립적이다.

### 성장 속도와 기존 정책

기존 threshold 300 / 1,000 / 2,500 EXP를 유지했다. NEW/REVIEW는 정답 10 EXP, 오답 2 EXP다. 기본 설정은 새 단어 5·새 문법 5, 일일 목표 10개이며, 80% 정답률을 가정하면 하루 84 EXP다. 학습량과 정답률에 따라 달라지는 예상치로, 실제 일일 계획·backlog 제한을 변경하지 않았다.

| 예시 활동량 | EXP/일 | Stage 2 | Stage 3 | Stage 4 |
| --- | ---: | ---: | ---: | ---: |
| 정규 10개, 모두 정답 | 100 | 3일 | 10일 | 25일 |
| 정규 10개, 80% 정답 | 84 | 4일 | 12일 | 30일 |
| NEW+REVIEW 합계 10개, 80% 정답 | 84 | 4일 | 12일 | 30일 |
| 정규 10개 + 선택적 복습 5개, 80% 정답 | 126 | 3일 | 8일 | 20일 |
| 정규 10개 + 5문제 Quiz 주 3회, 80% 정답 | 평균 102 | 약 3일 | 약 10일 | 약 25일 |
| 정규 10개 + 5문제 Quiz 하루 20회, 80% 정답 | 924 | 1일 | 2일 | 3일 |

Quiz EXP는 기존 LearnerProfile 누적 EXP에 포함되므로 Haru 성장에도 포함했다. 세션 중복 답안 EXP 방지는 유지하지만 여러 Quiz 세션 반복은 성장을 크게 앞당길 수 있다. 이번 작업은 EXP 정책을 바꾸지 않았으며 farming 제한은 후속 운영 정책이다. RETRAIN EXP 0, Grammar Confirmation EXP 0, SRS, StudyRecord, Daily Goal/Mission, Streak, Quiz 2.0의 기존 분리 규칙도 유지했다.

### Service와 asset 계약

`HaruPresentationService`가 기존 enum 기반 계산, 다음 성장까지 남은 EXP, 진행률, 이벤트 표시 여부와 resolver 결과를 기존 `CharacterStatus`에 모은다. Stage 4는 finalStage=true로 다음 progress bar를 숨긴다. `HaruAssetResolver`는 실제 resource 존재 여부를 검사하며 도메인 stage와 assetStage/path/fallbackUsed를 분리한다. Stage 4 파일이 없으면 Stage 3, 더 이전 파일도 없다면 존재하는 가장 가까운 이전 poster를 선택하고 모두 없으면 null을 반환한다.

제공된 Stage 1~3 파일의 중복 확장자 `.png.png`만 `.png`로 정리했다. 파일명 변경 전후 SHA-256이 동일하며 이미지 생성·시각 검사·편집·crop·변환은 수행하지 않았다. Stage 4 이미지는 새로 만들지 않았다. 이미지 디렉터리는 `/images/characters/haru`다. 기존 정적 manifest를 제거하고 같은 URL의 `GET /images/characters/haru/animation.json`을 resolver 기반 manifest v2로 제공한다. 기존 frame animator를 재사용하며 stage-aware CSS로 변경했다.

현재 새 디자인 Blink frame은 없다. 세 frame이 모두 있을 때만 supportsBlink=true와 frames/ambient Blink를 제공한다. 존재하지 않는 frame을 브라우저가 요청하지 않는다. 향후 Blink는 poster→01→02→03→poster, 90/70/110/70/120ms 계약을 사용한다. idle ambient는 8~16초 무작위 간격이고, study는 학습 중 상태를 유지하며 happy/goal-complete/growth 연출 후 idle로 복귀한다. hidden·state 전환·run id cancel과 reduced-motion을 유지한다. reduced-motion에서는 기본 poster만 표시하며 frame/motion을 실행하지 않는다.

실행 코드·CSS·template·테스트·현재 asset 계약 문서에서 이전 poster/frame 직접 참조를 제거했다. 과거 `current-features.md`의 검증 기록과 별도 design-prototype은 당시 상태를 기록한 자료로 보존했다. 상세 실행 계약은 `docs/haru-animation-assets.md`에 갱신했다.

### 성장 이벤트와 화면

기존 `LearnerProfile.pendingGrowthStageKey`를 재사용하고 nullable `presentedGrowthStageKey` 하나를 추가했다. 실제 EXP 지급 전후 stage가 다를 때만 최신 pending을 갱신한다. 기존 사용자의 누적 EXP를 초기화하거나 과거 성장 이벤트를 새로 생성하지 않는다. 기존 pending이 있으면 최신 안내 한 건만 유지하며 여러 단계를 연속 modal로 재생하지 않는다.

연출 전에 현재 사용자 Profile을 DB lock으로 확보해 한 요청만 claimed=true를 받는다. 새로고침·뒤로가기·동시 기기의 중복 연출을 막는다. 표시 확보와 안내 확인은 분리되어 페이지 이동으로 연출이 중단돼도 안내는 남는다. 오래된 stage의 acknowledgement는 새 성장 이벤트를 지우지 않는다. 서버가 표시 확보를 저장한 뒤 응답이 유실되는 경우 연출 재생을 보장할 수는 없으며, 이때도 명시적 확인 전 안내는 유지한다.

Home과 Today는 growth > goal-complete > happy > study > idle 우선순위를 사용한다. Mission 완료만으로 growth를 만들지 않는다. 성장 연출이 표시된 이후 Home은 일반 Mission 완료 정보로 돌아간다. Home은 현재 EXP, 단계 설명, 다음 성장까지 남은 EXP, progress만 제공하며 최종 단계에는 다음 bar가 없다. 새 전신 이미지가 잘리지 않도록 기존 Haru 영역의 음수 offset을 정리하고 Today 완료 화면의 compact한 이미지 기준 영역을 추가했다. 학습 화면의 본문과 결과를 덮는 modal은 없다.

### REST API와 Android

기존 `StudyOverview.character` DTO를 확장했다. 기존 field를 유지하면서 stageNumber, animationStageKey, assetStage, fallbackUsed, supportsBlink, animationManifestPath, finalStage, growthPresentationPending, pendingGrowthStageKey를 제공한다. 기존 study/account 관련 소비자는 기존 필드를 그대로 사용할 수 있다.

- `GET /api/v1/characters/current`: 현재 사용자 캐릭터 성장 상태. 조회만으로 이벤트를 소비하지 않는다.
- `POST /api/v1/characters/growth/present?stageKey=...`: 미표시 이벤트를 한 번 확보하고 claimed를 반환한다.
- 기존 `POST /api/v1/characters/growth/acknowledge?stageKey=...`: 일치하는 성장 안내 확인. 이전 stageKey 없는 요청도 호환 목적으로 유지한다.
- 기존 웹 `POST /character/growth/acknowledge`도 stageKey를 전송한다.

모든 개인 상태와 이벤트 처리는 인증된 현재 사용자 기준이며 userId/learnerId/profileId 입력으로 소유자를 선택하지 않는다. 기존 Spring Security/CSRF 정책을 유지한다. Android도 같은 Service와 durable 이벤트 상태를 사용할 수 있다.

### 검증 결과

`HaruGrowthV2Test`를 추가해 0/299/300/301/999/1000/2499/2500/4000 EXP, domain/asset stage 분리, final stage, 기존 null 호환, Today 성장과 완료 중복 POST, Mission-only 비성장, Quiz 성장, stale claim/ack, 사용자 격리, anonymous/CSRF와 frame availability를 확인했다. 기존 화면 asset 테스트는 실제 제공되는 Stage 1~3와 Stage 4 fallback을 검증하도록 갱신했다. 전체 Gradle 테스트는 **103개 중 102개 통과, 실패·오류 0개, APKG 조건부 테스트 1개 skipped**다.

기존 headless 브라우저 Blink lifecycle 테스트에 성장 표시 확보와 reduced-motion 전환 후 비재생 검증을 추가했고 통과했다. 현재 Blink 그림이 없으므로 이 테스트의 frame URL은 변경하지 않은 poster 바이트를 반환하는 fixture이며, 순서·timer·cancel·run id·network를 검증한다. 실제 Blink 이미지 시각 검수로 해석하지 않는다.

실제 MySQL 8.0.39 + Spring Boot + Chrome에서 별도 테스트 계정으로 실제 학습 API를 사용해 290→300, 990→1000, 2490→2500 EXP 경계를 확인했다. Stage 2는 Today 완료 POST, Stage 3/4는 정규 학습 답안 POST로 경계를 넘겼다. 각 성장에서 claimed=true는 한 번만 나오며 새로고침 후 pending animation=false였다. 동일 Today 완료 재전송 후 EXP는 300으로 유지됐다. Stage 4는 domain=4/asset=3/fallbackUsed=true였고 재로그인 후 유지됐으며 다음 progress bar는 없었다. Home·Today 완료·History·Weekly Report 및 1440×1000/390×844에서 DOM/network 기준 broken image, clipping, horizontal overflow, 실제 보이는 navigation 겹침, 404, JS 오류가 없었다. 숨겨진 navigation의 0-height rect는 overlap 판정에서 제외했다. reduced-motion에서는 poster만 표시했다.

남은 범위는 Stage 4 디자인, 새 디자인의 상태별/Blink artwork, 전역 Quiz 반복 EXP 운영 정책, 실제 이미지 시각 검수다. 별도 Haru EXP·JLPT 성장·게임 화폐·상점·배지·Android UI·push는 추가하지 않았다.

최종 `git diff --check`도 통과했다. 검증용 Spring Boot와 headless Chrome은 종료했으며 기존 MySQL 서비스와 사용자 데이터는 유지했다.

## 관리자 콘텐츠 검수 / 승인 콘솔 (2026-09-13)

기존 `ContentItem.reviewStatus`와 `published` 공개 정책, `ContentReviewHistory`, `GrammarCurationService`, curated 문법 네 모델을 유지하면서 `/admin` 관리자 콘솔을 추가했다. 기존 `UserRole.ADMIN`을 그대로 사용하며 `/admin/**`와 `/api/v1/admin/**`는 `ROLE_ADMIN`만 접근할 수 있다. anonymous는 로그인으로 이동하고 일반 사용자는 403을 받으며, 승인·반려 요청은 기존 CSRF 정책을 따른다.

관리자 IA는 `/admin`, `/admin/contents`, `/admin/contents/{id}`, `/admin/grammars/curation`, `/admin/grammars/curation/{type}/{id}`로 구성한다. Dashboard는 전체/WORD/GRAMMAR/curated PENDING 수와 최근 7일 승인·반려 수를 compact하게 보여준다. 일반 목록은 status, published, type, JLPT, keyword, source, 정렬을 DB `Specification`과 25건 pagination으로 처리한다. `findAll()` 뒤 메모리 필터는 사용하지 않는다. 상세는 정규화된 Word/Grammar, 모든 Meaning/Example과 연결 의미, source/license/attribution, `ImportedSourceRecord` 원본 필드, 최신순 감사 이력을 읽기 전용으로 보여준다.

일반 콘텐츠 승인은 기존 정책대로 `APPROVED + published=true`, 반려는 `REJECTED + published=false`로 처리한다. 최소 필수 Word 표기·읽기 및 Grammar 패턴·설명을 검증하며 반려 사유는 필수다. PENDING에서만 상태를 바꾸고 이미 같은 상태인 POST는 성공적인 idempotent 결과를 반환해 이력을 추가하지 않는다. 대상 row는 pessimistic write lock으로 다시 읽어 두 관리자의 동시 검수에서 lost update를 막는다. `ContentReviewHistory`에는 nullable 이전 상태와 reviewer를 보강해 기존 row와 호환하면서 새 관리자 작업의 대상, 전후 상태, 현재 인증 관리자, 시각, note를 남긴다. 기존 import-sample `/review`의 승인·반려·선택 승인도 같은 감사 서비스로 연결했고 선택 승인은 최대 20건으로 제한했다. 새 콘솔에는 bulk UI를 넣지 않았다.

curated 검수는 `GrammarEnrichment`, `GrammarRelation`, `GrammarComparison`, `GrammarConfirmationQuestion`을 `CurationRecordType`으로 분리해 각각 DB 필터, pagination, 상세, 승인, 반려를 제공한다. 원본 Grammar 승인과 자동 연동하지 않으며 각 record의 기존 `APPROVED + published` 공개 조건을 유지한다. `CurationReviewHistory`가 reviewer와 상태 전이를 record type/id별로 기록한다. 관리자 confirmation 상세와 API만 정답 여부를 제공하고 기존 public confirmation DTO는 choice id/text만 반환한다.

관리자 REST API는 `/api/v1/admin/dashboard`, `/api/v1/admin/contents[/{id}]`, `/api/v1/admin/contents/{id}/approve|reject`, `/api/v1/admin/grammar-curation[/{type}/{id}]`, `/api/v1/admin/grammar-curation/{type}/{id}/approve|reject`를 제공한다. reviewer id는 요청으로 받지 않고 현재 인증 계정만 사용한다. 잘못된 상태/입력은 400, 없는 대상은 404로 반환한다.

자동화 테스트는 anonymous/USER/ADMIN 접근, CSRF, DB 필터와 pagination, WORD/GRAMMAR/JLPT/keyword/source, 일반 승인·반려 및 중복 POST, reviewer/history, 공개 전 미노출과 승인/반려 후 공개 정책, 학습 데이터 불변, curated 네 종류의 독립 승인·반려, 관리자 정답 표시와 public 정답 미노출, 잘못된 요청의 4xx를 검증한다. 전체 `./gradlew.bat test` 결과는 **109개 중 108개 통과, 실패·오류 0개, APKG 조건부 smoke test 1개 skipped**이며 `git diff --check`도 통과했다.

실제 MySQL 8.0.39와 Spring Boot에서 소수 항목만 사용해 일반 콘텐츠 승인 2건·반려 1건, curated enrichment 승인 1건, 중복 승인 재전송, 감사 이력 reviewer, 재로그인 후 유지, 승인 콘텐츠 공개와 반려 콘텐츠 미노출, 사용자 StudyRecord 미변경을 확인했다. Chrome 1440×1000과 390×844에서 dashboard, 일반 목록/상세, curated 목록/상세와 모바일 2열 필터를 확인했다. 모든 화면의 `scrollWidth == clientWidth`였고 clipping, mobile navigation 겹침, 잘못된 badge, 중복 이력, JavaScript exception, 예상하지 않은 HTTP 4xx/5xx, missing asset/404가 없었다. 이 과정에서 Example DTO 필드명을 잘못 참조해 상세 화면에서 발생한 500을 수정하고 다시 검증했다.

이번 범위에는 대량 승인, 원본/Meaning/Example 편집, AI 검수, 자동 라이선스 승인, 콘텐츠 생성·재import, Flyway, 검색 엔진, Android admin UI를 포함하지 않았다. 다음 단계에는 검수 전용 테스트 fixture/DB 분리, 감사 이력 보존 정책, 상태 되돌리기와 correction workflow를 별도 운영 정책으로 정해야 한다.

## N5 콘텐츠 품질 감사 (2026-09-13)

관리자 검수 콘솔에 읽기 전용 `ContentQualityAuditService`를 추가했다. 감사 결과는 DB에 저장하지 않고 목록 한 페이지와 상세 조회 시 계산한다. 규칙은 단순한 저장 데이터 검사이며, 중복 판정과 출처 존재 여부만 배치 DB 조회한다. 따라서 오래된 캐시가 없고, 콘텐츠·import 원본·`reviewStatus`·`published`를 변경하지 않는다.

WORD는 표기/읽기/대표 의미 누락, 예문·번역 누락, source/sourceRef/JLPT 누락, 정규화된 표기+읽기 중복 후보, 과도한 meaning/reading 길이, markup·앞뒤 공백을 점검한다. GRAMMAR는 pattern/description/예문·번역/source/sourceRef/JLPT 누락, 정규화 pattern 중복 후보, 짧거나 긴 설명, markup·공백을 점검한다. ERROR는 필수 학습 데이터 누락, WARNING은 검토가 필요한 누락·중복, INFO는 형식/길이 힌트다. 언어학적 정오를 자동 판정하거나 자동 수정·승인·반려하지 않는다.

`/admin`에는 N5 WORD/GRAMMAR의 total·clean·info·warning·error 요약을 표시한다. `/admin/contents`와 `/api/v1/admin/contents`는 `qualityIssue`, `qualitySeverity`, `qualityIssueType` 필터와 기존 25건 DB pagination을 지원하며, 목록은 가장 높은 심각도와 건수를, 상세는 Quality Audit 항목을 표시한다. 집계와 필터 모두 DB Specification/correlated query를 사용하고 `findAll()+stream`으로 전체 N5를 읽지 않는다. 일반 사용자 API와 기존 공개 정책에는 감사 정보가 노출되지 않는다.

자동화 테스트는 blank reading, 의미/예문 누락, WORD/GRAMMAR 중복 후보, blank grammar description, sourceRef 누락, clean 콘텐츠, severity, N5 issue filter/pagination, ADMIN 권한/CSRF와 감사 조회의 review/published 비변경을 검증한다. 전체 `./gradlew.bat test`는 110 tests, 0 failures, 0 errors, 1 skipped를 통과했다. 실제 MySQL/브라우저 검증 수치와 화면 결과는 배포 전 현재 데이터로 재확인한다.

MySQL 8.0.39 read-only audit snapshot (2026-09-13): N5 WORD 780 (ERROR 0, WARNING 11, INFO 0, clean 769, duplicate candidate 11); N5 GRAMMAR 341 (ERROR 0, WARNING 262, INFO 3, clean 76, duplicate candidate 134). Counts are reviewer candidates, not automatic review outcomes.

## 콘텐츠 정규화/후보 검토 파이프라인 (2026-09-15~09-17)

기존 `ContentSource`/`ContentReviewHistory` 공개 검수와 별개로, JLPT-MAX 원본을 production 콘텐츠로 승격하기 전에 거치는 **private, read-only-safe 파이프라인**을 추가했다. 이 파이프라인이 만드는 모든 데이터는 현재 공개 검색·화면·API에 노출되지 않으며, 어떤 단계도 `ContentItem`/`Word`/`Grammar`를 자동으로 생성하거나 공개하지 않는다.

단계:

1. **Safe content release workflow** — `content_release_batches`/`content_release_batch_items`(V4)로 콘텐츠 공개 배치와 immutable 이력을 관리한다.
2. **Private APKG staging** — `PrivateApkgExtractor`가 `private_apkg_notes`(V5)에 원본 note 필드를 저장하되, audio/media binary는 추출하지 않고 `[sound:...]`/`<audio>` 같은 텍스트 참조만 제거한다.
3. **정규화 파서** — `VocabularyNormalizationParser`/`GrammarNormalizationParser`는 side-effect-free 순수 함수로, staging 원본을 입력받아 불변 정규화 결과(`NormalizedExample`/`NormalizedGrammarExample` 등)를 만든다. DB/Entity에 결합하지 않는다.
4. **정규화 후보 영속화** — `NormalizedContentCandidate`와 하위 Vocabulary/Grammar 상세(`normalized_content_candidates`, `normalized_vocabulary_candidates`, `normalized_grammar_candidates` 등, V6)가 정규화 결과를 private 테이블에 저장한다.
5. **Dedup/conflict 분석** — `NormalizedCandidateConflictAnalyzer`가 `normalized_candidate_match_pairs`/`normalized_candidate_match_evidence`(V7)에 후보 간 SAME_CONTENT/POSSIBLE_DUPLICATE 관계와 근거를 기록한다.
6. **사람 검토** — `/admin/normalized-candidates/reviews`에서 관리자가 각 pair에 대해 `NormalizedCandidatePairReview`(V8, 이력은 `normalized_candidate_pair_review_history`)로 판정을 남긴다.
7. **승격 준비도 조회(dry-run)** — `/admin/normalized-candidates/promotion-readiness`는 quality/pair/production-mapping/source-rights/production-identity 축을 모두 조회해 각 후보가 아직 승격 불가능한 이유(`PromotionReadinessIssueCode`)를 보여주는 **100% read-only** 화면이다. 어떤 축도 자동으로 통과시키지 않으며, production/private 테이블에 row를 쓰지 않는다.

v2.1.1 APKG 기준 실측(2026-09-17): Vocabulary 후보 9,160건, Grammar 후보 1,078건 모두 `READY_FOR_DRAFT_PROMOTION 0`이다. 이는 버그가 아니라 설계된 결과로, production JLPT `Level` row가 아직 N5만 시드된 profiling 환경 특성(`JLPT_LEVEL_UNMAPPABLE`)과 production identity/slug 정책이 아직 확정되지 않은 것(`PRODUCTION_IDENTITY_POLICY_UNRESOLVED`)이 공통 원인이다. 자세한 수치와 근거는 `docs/development/IMPLEMENTATION_LOG.md`를 참고한다.

이 범위에서 아직 구현하지 않은 것: 실제 production 승격(ContentItem/Word/Grammar/Meaning/Example insert), 중복 쌍의 canonical winner 자동 병합, 공개 publication 자동화. 이 결정들은 이후 별도 작업으로 남아 있다.
