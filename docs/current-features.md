# Japanese 개발 현황

최종 정리: **2026-09-11**

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
4. 이메일 인증, 비밀번호 재설정, 표시 이름 변경 같은 계정 운영 기능을 추가한다.

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

Android도 같은 규칙을 쓸 수 있도록 `GET /api/v1/study/today/session`, `POST /api/v1/study/today/session/{slug}/complete`를 추가했다. 기존 `GET /api/v1/study/today`는 계획 미리보기 API로 유지한다.

Service 및 MockMvc E2E 테스트로 세션 시작, 첫 카드 완료, 설정 변경 뒤 이어하기, 같은 완료 요청 재전송, 전체 완료, 날짜별 리포트 반영과 웹/API 세션 조회를 검증했다. `./gradlew.bat test`는 56개 통과, 실패·오류 0건으로 완료됐다. 실제 APKG 경로가 있을 때만 실행하는 기존 smoke test 1개는 조건부 제외 상태다.
