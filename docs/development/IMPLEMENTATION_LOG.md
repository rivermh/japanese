# Japanese 구현 기록

## 2026-09-15 — Phase 1A / Ticket A: Source rights policy

### 작업 목적

콘텐츠 source별 재배포 자격을 명시적으로 저장하고 판정할 수 있게 만들고, 권리 상태가 `ALLOWED`가 아닌 source의 콘텐츠가 새 승인 과정에서 사용자에게 공개되지 않도록 차단했다. 실제 콘텐츠의 대량 승인·공개는 수행하지 않았다.

### 변경 파일

- `japanese/src/main/java/com/japanese/content/entity/ContentSourceRightsStatus.java`
- `japanese/src/main/java/com/japanese/content/entity/ContentSource.java`
- `japanese/src/main/java/com/japanese/content/entity/ContentItem.java`
- `japanese/src/main/java/com/japanese/content/entity/GrammarEnrichment.java`
- `japanese/src/main/java/com/japanese/content/entity/GrammarRelation.java`
- `japanese/src/main/java/com/japanese/content/entity/GrammarComparison.java`
- `japanese/src/main/java/com/japanese/content/entity/GrammarConfirmationQuestion.java`
- `japanese/src/main/java/com/japanese/content/repository/ContentSourceRepository.java`
- `japanese/src/main/java/com/japanese/content/service/ContentSourceRightsService.java`
- `japanese/src/main/java/com/japanese/content/service/ContentReviewService.java`
- `japanese/src/main/java/com/japanese/content/service/AdminContentReviewService.java`
- `japanese/src/main/java/com/japanese/content/controller/AdminContentSourceRightsApiController.java`
- `japanese/src/main/java/com/japanese/content/controller/AdminContentReviewExceptionHandler.java`
- `japanese/src/main/java/com/japanese/content/controller/ContentReviewController.java`
- `japanese/src/main/java/com/japanese/content/dto/AdminContentSourceRightsModels.java`
- `japanese/src/main/java/com/japanese/content/dto/AdminContentReviewModels.java`
- `japanese/src/main/resources/templates/admin/content-detail.html`
- `japanese/src/main/resources/templates/content-review.html`
- `japanese/src/main/resources/templates/content-review-detail.html`
- `japanese/src/main/resources/db/migration/mysql/V3__add_content_source_rights.sql`
- `japanese/src/main/resources/db/migration/h2/V3__add_content_source_rights.sql`
- `japanese/src/test/java/com/japanese/content/service/ContentSourceRightsServiceTest.java`
- `japanese/src/test/java/com/japanese/content/service/AdminContentReviewServiceTest.java`
- `japanese/src/test/java/com/japanese/content/controller/AdminContentReviewControllerTest.java`
- `japanese/src/test/java/com/japanese/content/importer/ApkgVocabularyImporterTest.java`
- `japanese/src/test/java/com/japanese/migration/FlywayMigrationTest.java`
- `docs/development/IMPLEMENTATION_LOG.md`

### 핵심 구현 내용

- `ContentSourceRightsStatus`에 `UNKNOWN`, `MANUAL_REVIEW_REQUIRED`, `ALLOWED`, `BLOCKED`를 정의했다.
- `ContentSource`에 `rightsStatus`, `rightsReviewedAt`, `rightsReviewNote`, `attributionRequired`를 추가했다.
- 신규 source의 기본 권리 상태를 Entity와 DB 모두 `UNKNOWN`으로 설정했다.
- 허용된 상태 전환만 가능하게 하고, 모든 상태 전환에 검토 메모와 검토 시각을 기록한다.
- attribution이 필수인 source는 attribution 문구 없이 `ALLOWED`로 전환할 수 없게 했다.
- `ContentSourceRightsService`가 sourceRef 누락, 미등록 source, 비정규 sourceRef, 권리 미허용, attribution 누락을 공통 판정하고 차단 코드와 이유를 반환한다.
- 관리자 콘텐츠 승인, legacy review 승인, grammar enrichment/relation/comparison/confirmation 승인 경로가 같은 source rights 판정을 사용한다.
- 내용 검수는 통과했지만 source 권리가 허용되지 않은 경우 `APPROVED + published=false`로 저장하고 차단 이유를 review history에 남긴다.
- 관리자 전용 API `GET /api/v1/admin/content-sources`, `GET /api/v1/admin/content-sources/{id}`, `POST /api/v1/admin/content-sources/{id}/rights`를 추가했다.
- 관리자 콘텐츠 상세에 source rights 상태와 공개 차단 이유를 표시하고, 승인 버튼 문구가 실제 공개 가능 여부를 반영하도록 했다.
- 내부 권리 검토 메모는 public 콘텐츠 API에 추가하지 않았다.
- 기존 이미 공개된 콘텐츠는 변경하거나 일괄 비공개 처리하지 않았다.

### DB migration

- MySQL/H2에 `V3__add_content_source_rights.sql`을 추가했다.
- `content_sources`에 `rights_status`, `rights_reviewed_at`, `rights_review_note`, `attribution_required`를 추가했다.
- 기존 source는 자동 `ALLOWED` 처리하지 않고 `UNKNOWN`, `attribution_required=false`로 보수적으로 backfill한다.
- `content_items`, 기존 `published`, `review_status`, 콘텐츠 데이터는 변경하지 않는다.
- V1/V2 migration은 수정하지 않았고 destructive SQL은 추가하지 않았다.
- H2에서 빈 DB V1→V2→V3, 기존 V2 DB→V3, 반복 migrate 0건, 기존 공개 콘텐츠 보존을 자동 검증했다.
- MySQL V3는 additive SQL과 보수적 기본값을 정적 안전성 테스트로 확인했으며 실제 MySQL rehearsal은 후속 운영 검증으로 남는다.

### 테스트 결과

- 명령: `.\gradlew.bat test`
- 결과: 143 tests, 0 failures, 0 errors, 1 skipped
- 신규 검증: 기본 `UNKNOWN`, 허용/차단 판정, 상태 전환, 필수 attribution, 관리자 API, ContentItem/curation 공개 차단, import source의 명시적 권리 승인, V3 migration 및 기존 공개 행 보존

### git diff --check 결과

- 통과 (`exit code 0`)
- 줄바꿈 변환 안내 외 whitespace 오류 없음

### 남은 위험 / 후속 작업

- 실제 MySQL 환경에서 V3 migration rehearsal이 필요하다.
- source rights는 현재 최신 note/time을 저장하며 별도의 immutable 변경 이력과 reviewer FK는 없다.
- 권리 미확인 상태에서 이미 내용 승인된 콘텐츠를 권리 허용 후 다시 공개하는 명시적 흐름은 Ticket C에서 구현해야 한다.
- 기존의 잘못된 `published/reviewStatus` 조합 정규화와 기존 공개 콘텐츠 운영 점검도 Ticket C 및 dry-run 이후 범위다.
- persisted Today session의 unpublish 처리, batch dry-run/approval, duplicate 판정, Word/Grammar 전체 release gate는 후속 Ticket 범위다.

### Commit / push 여부

- Commit하지 않음
- Push하지 않음
- 기존 콘텐츠 대량 승인·공개 및 production DB 데이터 변경 없음

## 2026-09-15  Phase 1A / Ticket B: Release Gate / Quality Audit

### 작업 목적

- 기존 Quality Audit과 Ticket A의 source rights 정책을 조합해 콘텐츠의 공개 가능 여부와 이유를 결정적으로 계산한다.
- 콘텐츠 품질 검수 승인과 사용자 공개 자격을 분리하고, blocker 또는 수동 검토 항목이 있는 콘텐츠의 자동 공개를 차단한다.

### 변경 파일

- `japanese/src/main/java/com/japanese/content/service/ContentReleaseDecision.java`
- `japanese/src/main/java/com/japanese/content/service/ContentReleaseIssueClassification.java`
- `japanese/src/main/java/com/japanese/content/service/ContentReleaseIssueCode.java`
- `japanese/src/main/java/com/japanese/content/service/ContentReleaseGateService.java`
- `japanese/src/main/java/com/japanese/content/dto/AdminContentReviewModels.java`
- `japanese/src/main/java/com/japanese/content/service/AdminContentReviewService.java`
- `japanese/src/main/java/com/japanese/content/service/ContentReviewService.java`
- `japanese/src/main/resources/templates/admin/content-detail.html`
- `japanese/src/test/java/com/japanese/content/service/ContentReleaseGateServiceTest.java`
- `japanese/src/test/java/com/japanese/content/service/AdminContentReviewServiceTest.java`
- `japanese/src/test/java/com/japanese/content/controller/AdminContentReviewControllerTest.java`
- `docs/development/IMPLEMENTATION_LOG.md`

### 핵심 구현

- 계산형 Release Gate 결과를 `RELEASABLE`, `MANUAL_REVIEW_REQUIRED`, `BLOCKED`로 구분했다.
- 각 issue는 machine-readable code와 `PUBLICATION_BLOCKER`, `MANUAL_REVIEW`, `INFORMATIONAL` 분류를 가진다.
- Quality Audit의 ERROR/WARNING/INFO를 공개 판정에 그대로 대응시키지 않고, 공개 필수 조건에 맞춰 별도로 해석한다.
- 동일 콘텐츠와 동일 DB 상태에서는 같은 순서와 코드로 결과가 나오도록 결정적인 평가 순서를 사용한다.

### Word gate

- Word entity, 표기, 읽기, 정규화 가능한 검색 key, 비어 있지 않은 한국어 의미, JLPT 레벨, 식별 가능한 source와 허용된 source rights, 치명적인 파싱 손상을 공개 blocker로 판정한다.
- 중복 후보, 예문/번역 부족, 품사 부족, 비정상적으로 긴 읽기, markup 의심은 수동 검토 항목으로 판정한다.
- pitch accent, audio, example reading 부재는 공개 blocker로 취급하지 않는다.

### Grammar gate

- Grammar entity, pattern, explanation, JLPT 레벨, 식별 가능한 source와 허용된 source rights, 치명적인 파싱 손상을 공개 blocker로 판정한다.
- 중복 후보, 설명 길이 이상, 예문/번역 부족, markup 의심은 수동 검토 항목으로 판정한다.
- connection, enrichment, relation, comparison, confirmation question 부재는 공개 blocker로 취급하지 않는다.

### Quality Audit 연결

- 기존 `ContentQualityAuditService` 결과를 재사용하며 기존 severity와 관리자 Quality Audit 화면의 의미는 변경하지 않았다.
- JLPT 누락처럼 Quality WARNING이어도 공개 필수 조건이면 blocker로, 중복/예문 부족처럼 WARNING이어도 보완 가능한 항목이면 manual review로 분류한다.
- 긴 meaning과 주변 공백 등은 informational로 유지한다.

### Source rights 연결

- 별도 license 로직을 만들지 않고 `ContentSourceRightsService.releaseEligibility`를 단일 판정 경로로 재사용한다.
- `UNKNOWN`, `MANUAL_REVIEW_REQUIRED`, `BLOCKED`, source 누락/미등록/비정규 참조, 필수 attribution 누락은 공개 blocker다.
- `ALLOWED`만 source rights gate를 통과한다.

### Admin integration

- 관리자 콘텐츠 상세에 Release Gate 결정, 최종 이유, blocker/manual/informational issue, source rights 상태를 표시한다.
- 단건 및 legacy/batch 승인 경로는 공개 직전에 Release Gate를 다시 계산한다.
- `BLOCKED` 또는 `MANUAL_REVIEW_REQUIRED` 콘텐츠는 내용 검수를 `APPROVED`로 기록할 수 있지만 `published=false`를 유지하며 이유를 review history에 남긴다.
- override, batch workflow, 기존 상태 정규화는 추가하지 않았다.

### DB migration

- 없음. Release Gate 결과는 현재 콘텐츠와 source 상태에서 읽기 전용으로 계산하며 영속화하지 않는다.
- V1/V2/V3 migration은 수정하지 않았다.

### 테스트 결과

- 명령: `./gradlew.bat test`
- 결과: 152 tests, 0 failures, 0 errors, 1 skipped
- Word/Grammar 필수 blocker, source rights 네 상태, 중복, 예문/번역, 품사/읽기 길이, 설명 길이, markup, optional 데이터 부재, 관리자 상세 노출과 승인·공개 분리를 검증했다.
- 기존 Content review, Quality Audit, import, Dictionary/Search, Today, Quiz, Statistics, Ticket A source rights 및 Phase 0 테스트가 모두 통과했다.

### git diff --check 결과

- 통과 (`exit code 0`)
- 줄바꿈 변환 안내 외 whitespace 오류 없음

### 남은 위험 / 후속 작업

- 수동 검토 항목을 사람이 명시적으로 승인해 공개하는 override 및 immutable 근거 기록은 Ticket C 범위다.
- 기존 `published/reviewStatus` 불일치 데이터의 탐지·정규화, unpublish/republish, rollback manifest는 아직 구현하지 않았다.
- duplicate adjudication, batch dry-run/approval, N5 실제 공개는 후속 Ticket에서 처리해야 한다.
- 치명적 파싱 손상은 현재 NUL 및 Unicode replacement character를 보수적으로 차단하며, 실제 import corpus에 맞춘 추가 signature는 dry-run 결과로 보강해야 한다.

### Commit / push 여부

- Commit하지 않음
- Push하지 않음
- 실제 콘텐츠 승인·공개 및 production DB 데이터 변경 없음

## 2026-09-15  Phase 1A / Ticket C: Publication invariant / Unpublish safety

### 작업 목적

- 콘텐츠 검수 상태와 사용자 공개 상태를 분리하면서, 새 공개 전환은 Release Gate를 반드시 통과하도록 제한한다.
- 비공개·재공개·재검토 과정에서 기존 학습 기록과 사용자 정리 관계를 삭제하지 않고, 진행 중 Today/Quiz 세션을 안전하게 유지한다.

### 변경 파일

- `japanese/src/main/java/com/japanese/content/entity/ContentItem.java`
- `japanese/src/main/java/com/japanese/content/repository/ContentItemRepository.java`
- `japanese/src/main/java/com/japanese/content/repository/ContentReviewHistoryRepository.java`
- `japanese/src/main/java/com/japanese/content/repository/BookmarkRepository.java`
- `japanese/src/main/java/com/japanese/content/service/ContentPublicationService.java`
- `japanese/src/main/java/com/japanese/content/service/AdminContentReviewService.java`
- `japanese/src/main/java/com/japanese/content/service/ContentReviewService.java`
- `japanese/src/main/java/com/japanese/content/service/BookmarkService.java`
- `japanese/src/main/java/com/japanese/content/controller/AdminContentReviewController.java`
- `japanese/src/main/java/com/japanese/content/controller/AdminContentReviewApiController.java`
- `japanese/src/main/java/com/japanese/content/controller/ContentReviewController.java`
- `japanese/src/main/java/com/japanese/learning/entity/TodayStudySessionItem.java`
- `japanese/src/main/java/com/japanese/learning/repository/StudyCollectionItemRepository.java`
- `japanese/src/main/java/com/japanese/learning/service/TodayStudySessionService.java`
- `japanese/src/main/java/com/japanese/learning/service/DailyMissionService.java`
- `japanese/src/main/java/com/japanese/learning/service/LearningService.java`
- `japanese/src/main/java/com/japanese/learning/service/StudyCollectionService.java`
- `japanese/src/main/resources/templates/admin/content-detail.html`
- `japanese/src/main/resources/templates/content-review-detail.html`
- `japanese/src/test/java/com/japanese/content/service/ContentPublicationServiceTest.java`
- `japanese/src/test/java/com/japanese/content/controller/AdminContentReviewControllerTest.java`
- `japanese/src/test/java/com/japanese/content/importer/ApkgVocabularyImporterTest.java`
- `japanese/src/test/java/com/japanese/learning/service/TodayStudySessionServiceTest.java`
- `japanese/src/test/java/com/japanese/learning/service/ContentVisibilitySafetyTest.java`
- `japanese/src/test/java/com/japanese/learning/service/QuizSessionServiceTest.java`
- `docs/development/IMPLEMENTATION_LOG.md`

### Publication invariant

- 정상 operation의 상태를 `PENDING/REJECTED + unpublished`, `APPROVED + unpublished/published`로 제한했다.
- 새 `published=true` 전환은 `APPROVED` 확인 후 Ticket B Release Gate를 즉시 재평가하고 `RELEASABLE`일 때만 수행한다.
- `MANUAL_REVIEW_REQUIRED`와 `BLOCKED`는 공개할 수 없으며 결정적 예외 코드가 포함된 오류를 반환한다.
- 기존 invalid 데이터는 변경하지 않고 읽기 전용 diagnostic으로 개수를 확인한다.

### Publish / unpublish / republish

- `ContentPublicationService`에 명시적인 publish, unpublish, republish operation을 집중했다.
- unpublish는 `APPROVED + published=true`를 `APPROVED + published=false`로 바꾸며 사유를 필수로 받는다.
- republish는 과거 공개 여부에 의존하지 않고 현재 Release Gate를 다시 통과해야 한다.
- 관리자 Web/API 상세에 상태별 publish, unpublish, republish action을 연결했다.

### Review reopen

- 재검토는 콘텐츠를 먼저 안전하게 미공개로 만들고 `PENDING + published=false`로 전환한다.
- 관리자 및 legacy 검수 화면 모두 재검토 사유를 필수로 받는다.

### Invalid-state diagnostic

- `published=true`이면서 reviewStatus가 null/PENDING/REJECTED인 기존 행을 count하는 read-only repository query와 관리자 diagnostic API를 추가했다.
- diagnostic은 기존 행을 수정하거나 자동 정규화하지 않는다.

### Today session 처리

- persisted Today snapshot에서 unpublished된 미완료 항목은 `withdrawn`으로 처리하되 session item 관계와 원래 position은 보존한다.
- withdrawn 현재/미래 항목은 학습 결과를 저장하지 않고 건너뛰며, 다음 공개 항목의 원래 순서로 진행한다.
- 대체 콘텐츠를 삽입하거나 세션을 재생성하지 않는다.
- answer 직전 콘텐츠 행을 잠가 unpublish와 answer 경합에서 공개 상태를 다시 확인한다.
- 표시 target/count에서는 withdrawn 항목을 제외해 완료 불가능한 Daily Mission을 만들지 않는다.

### Bookmark 처리

- bookmark relation은 삭제하지 않는다.
- 일반 bookmark 목록과 표시 count에는 published 콘텐츠만 포함한다.
- 콘텐츠를 republish하면 기존 relation으로 다시 표시된다.

### StudyCollection 처리

- collection-content relation은 삭제하지 않는다.
- 상세 목록과 사용자 표시 count를 published 콘텐츠 기준으로 통일했다.
- republish 후 기존 collection relation이 다시 노출된다.

### Quiz / history 영향

- 진행 중 QuizSession은 기존 prompt/answer snapshot과 item order를 유지하며 content unpublish 뒤에도 재개·답안 처리가 가능하다.
- 새 Quiz candidate의 unpublished 제외 정책은 변경하지 않았다.
- LearningProgress, StudyRecord, QuizAttempt, bookmark, collection 관계를 publication operation에서 삭제하지 않는다.
- review history note에 `[APPROVE_AND_PUBLISH]`, `[APPROVE_UNPUBLISHED]`, `[PUBLISH]`, `[UNPUBLISH]`, `[REPUBLISH]`, `[REOPEN_REVIEW]`와 published 전후 상태 및 사유를 기록한다.
- visibility history가 최근 승인 통계를 중복 증가시키지 않도록 실제 status transition만 승인 건수로 집계한다.

### DB migration 여부

- 없음. 기존 `ContentReviewHistory`와 nullable result를 재사용했고 V1/V2/V3 migration은 수정하지 않았다.
- 기존 콘텐츠 상태와 사용자 데이터를 대량 변경하는 SQL은 추가하지 않았다.

### 문서 정리

- 일회성 작업 문서 `HARU_UI_CONSOLIDATION_TASK.md.md`와 `REMAINING_UI_REDESIGN.md`를 현재 코드 및 Master Feature Matrix와 대조했다.
- 두 문서의 작업 범위는 구현 완료됐거나 Matrix backlog에 반영되어 있어 삭제했다.

### 테스트 결과

- 명령: `./gradlew.bat test`
- 결과: 162 tests, 0 failures, 0 errors, 1 skipped
- publication 상태 전환과 gate 재검증, 이력/사유, invalid diagnostic, 학습 데이터 보존, Today withdrawn current/future/reload, bookmark/collection relation 보존·재노출, QuizSession snapshot을 검증했다.
- Ticket A source rights, Ticket B Release Gate, Phase 0 Today/due 및 전체 기존 회귀 테스트가 통과했다.

### git diff --check 결과

- 통과 (`exit code 0`)
- 줄바꿈 변환 안내 외 whitespace 오류 없음

### 남은 위험 / 후속 작업

- production DB의 invalid-state diagnostic 결과를 실제 정규화하기 전에 dry-run과 운영 승인이 필요하다.
- explicit manual-review override, batch dry-run/approval, rollback manifest와 duplicate adjudication은 후속 Ticket 범위다.
- Today withdrawn 상태는 기존 nullable result로 표현하므로 별도 withdrawal reason/audit column은 없다.
- 실제 대량 콘텐츠 및 MySQL 환경에서 publication query와 session lock 경합 검증이 필요하다.

### Commit / push 여부

- Commit하지 않음
- Push하지 않음
- 기존 사용자 학습 기록 삭제 없음
- 실제 콘텐츠 대량 승인·공개 및 production DB 데이터 변경 없음

## 2026-09-15  Phase 1A / Ticket D: Batch Dry-run / Preclassification

- 작업 목적: 콘텐츠를 변경하지 않고 필터 대상의 Release Gate 결과를 미리 분류하고 Ticket E 실행 전 대상 집합을 재검증할 수 있는 읽기 전용 preview를 제공했습니다.
- 변경 파일: `ContentReleaseDryRunService`, `AdminContentReleaseDryRunModels`, `ContentReleaseGateService` batch 평가, 관리자 API/Controller, `admin/content-dry-run.html`, 관리자 목록 링크, `ContentReleaseDryRunServiceTest`.
- dry-run architecture: DB Specification으로 scope를 먼저 적용하고 ID 오름차순 100건 페이지로 읽습니다. 결과에 고정된 ordered targetIds를 함께 반환하며, 기존 `ContentReleaseGateService`와 batched quality audit/source-rights cache를 사용하고 상태를 저장하지 않습니다.
- filter/scope: ContentType, JLPT, reviewStatus(PENDING은 null 포함), published, source, source rights status를 지원합니다. source rights status도 DB subquery로 대상 필터에 적용합니다.
- classification: RELEASABLE/MANUAL_REVIEW_REQUIRED/BLOCKED decision 수와 PUBLICATION_BLOCKER/MANUAL_REVIEW/INFORMATIONAL 및 issue code별 수를 제공합니다.
- source rights/duplicate: UNKNOWN, MANUAL_REVIEW_REQUIRED, ALLOWED, BLOCKED와 미등록/누락 별도 집계, 기존 duplicate detector 기반 duplicate candidate 수를 제공합니다.
- sampling: 전체/blocked/manual 각각 ID 오름차순 최대 10건의 deterministic sample을 반환합니다.
- digest/gate version: ordered target의 핵심 release 상태와 gate 결과를 SHA-256으로 생성하며 gate version은 `phase1a-v1`입니다. 대상/상태/판정이 변하면 digest가 달라집니다.
- API/UI: 관리자 전용 `POST /api/v1/admin/content-release/dry-run`과 관리자 `/admin/contents/dry-run` preview 화면을 추가했습니다. batch approve/publish action은 제공하지 않습니다.
- 실제 N5 WORD dry-run: 개발 DB를 직접 대상으로 하는 실행은 수행하지 않았습니다. 테스트 격리 DB에서 N5/WORD filter, 집계, digest, read-only 동작을 검증했으며 운영 DB 연결/변경 위험을 만들지 않았습니다.
- 테스트: targeted dry-run test 4건과 관리자 API 권한 및 관리자 화면 렌더링/read-only 테스트를 포함해 전체 168 tests, 0 failures, 0 errors, 1 skipped 통과.
- git diff --check: 통과(exit code 0; 기존 파일의 CRLF 변환 경고만 표시).
- 남은 위험/후속 작업: Ticket E에서 preview target IDs/digest와 현재 상태를 비교하는 batch approval 및 immutable history가 필요합니다. 대량 MySQL 환경에서는 EXPLAIN과 운영 read-only rehearsal이 필요합니다.
- commit/push: 수행하지 않았습니다.

## 2026-09-15  Phase 1A / Ticket E: Batch Approval / Immutable History

- 작업 목적: Ticket D preview의 ordered targetIds, digest, gateVersion을 실제 실행 직전에 동일 기준으로 재검증하고, 원자적 batch publication과 immutable item manifest 및 안전한 rollback 근거를 제공했습니다.
- 변경 파일: `ContentReleaseBatch`, `ContentReleaseBatchItem`, batch status/mode enum, batch repositories, `ContentReleaseBatchService`, batch DTO/exception, `ContentPublicationService`, `ContentReleaseDryRunService`, 관리자 API/exception handler, ContentItem/ContentSource lock query, MySQL/H2 V4 migration, batch/migration/controller tests, Master Feature Matrix.
- batch execution architecture: request는 고정 ordered targetIds, preview digest, gateVersion, mode, note와 explicit manual overrides를 받습니다. filter 재검색 결과를 실행 대상으로 사용하지 않습니다.
- stale preview validation: transaction 안에서 target ContentItem과 등록 source rows를 deterministic하게 pessimistic lock한 뒤 Ticket D의 공통 digest 계산으로 재평가합니다. target set/order, digest 또는 gateVersion이 다르면 전체 실행을 거부합니다.
- gate version: `ContentReleaseDryRunService.GATE_VERSION`의 `phase1a-v1`을 preview/execution이 함께 사용합니다.
- publication mode: 기본 `RELEASABLE_ONLY`, 제한적 `ALLOW_EXPLICIT_MANUAL_OVERRIDES`를 제공합니다. 이미 published target과 REJECTED target은 batch 실행을 거부합니다.
- manual override 정책: PUBLICATION_BLOCKER는 override할 수 없습니다. MANUAL_REVIEW issue code 전체를 item별로 정확히 acknowledge하고 reason을 남긴 경우에만 override mode에서 공개합니다.
- atomicity/concurrency: 모든 target/gate/override 검증을 mutation 전에 끝내며 하나라도 실패하면 batch와 콘텐츠 변경 전체가 rollback됩니다. content/source lock으로 검증과 publication 사이 상태 변경을 차단합니다. 동일 gateVersion+digest 요청은 기존 batch를 반환해 중복 이력을 만들지 않습니다.
- batch history/item manifest: reviewer, note, gateVersion, digest, count/status/timestamps와 각 item의 순서, before/after review/publication 상태, decision, issue snapshot, override snapshot, source/rights snapshot을 V4 테이블에 저장합니다. executed manifest는 일반 수정 API를 제공하지 않습니다.
- rollback manifest/operation: EXECUTED batch만 reason과 함께 rollback할 수 있습니다. 모든 item의 현재 상태가 manifest after-state와 일치하는지 먼저 검사하며 conflict 하나라도 있으면 전체 거부합니다. 상태가 일치하면 publication/review 상태만 before-state로 복원하고 ContentReviewHistory에 batch ID를 기록합니다. 학습 데이터와 콘텐츠 본문은 변경하지 않습니다.
- API: `POST /api/v1/admin/content-release/batches`, `GET /api/v1/admin/content-release/batches/{id}`, `POST /api/v1/admin/content-release/batches/{id}/rollback`; 기존 관리자 security가 적용됩니다.
- DB migration: additive MySQL/H2 `V4__add_content_release_batch_history.sql`. 기존 content/source/review/learning data를 변경하거나 삭제하지 않습니다.
- 실제 batch 실행 여부: 테스트 fixture의 synthetic content에만 실행했습니다. 실제 JLPT/imported content approve/publish 및 source rights 변경은 수행하지 않았습니다.
- Master Matrix 갱신: Phase 0 HEAD를 historical baseline으로 유지하고 Phase 1A A~E working tree 상태, V1~V4, 공개 콘텐츠 품질 위험 PARTIALLY RESOLVED, 남은 pilot/운영 위험과 177 test 결과만 부분 갱신했습니다. feature totals는 변경하지 않았습니다.
- 테스트 결과: 177 tests, 0 failures, 0 errors, 1 skipped.
- git diff --check: 통과(exit code 0).
- 남은 위험: 실제 source license 확인, production dry-run, duplicate adjudication, N5 pilot, MySQL rehearsal/EXPLAIN, duplicate peer가 실행 중 새로 삽입되는 극단적 경쟁 조건 검증이 남아 있습니다.
- commit/push: 수행하지 않았습니다.

## 2026-09-15  Phase 1A / Ticket F: Admin Batch Release UX

- 실제 작업/검증일: 2026-09-16 (섹션 제목은 요청한 Ticket 표기 유지).
- 작업 목적: Ticket D/E backend를 관리자 필터 → Dry-run → 개별 검토 → 명시적 실행 확인 → 이력/manifest → 안전한 rollback 화면으로 연결했습니다.
- 변경 파일: `AdminContentReleaseController`, `AdminContentReviewController`, `AdminContentReviewExceptionHandler`, `AdminContentReleaseDryRunModels`, `AdminContentReleaseBatchModels`, `ContentReleaseDryRunService`, `ContentReleaseBatchService`, `ContentReleaseBatchRepository`, admin `content-list.html`/`content-dry-run.html`/`content-batch-history.html`/`content-batch-detail.html`, `admin-release.css`, `admin-release.js`, `AdminContentReviewControllerTest`, `ContentReleaseDryRunServiceTest`, `src/test/js/admin-release.test.cjs`, `src/test/js/admin-release.browser.cjs`, Master Matrix 및 이 로그.
- admin navigation: 콘텐츠 목록에서 Batch Release와 실행 이력으로 진입합니다. `/admin/contents/dry-run`, `/admin/content-release/batches`, `/admin/content-release/batches/{id}`는 기존 ADMIN 보안 규칙을 사용합니다.
- filter/dry-run UX: 최초 진입은 조회하지 않습니다. 유형/JLPT는 명시 선택, 기본 PENDING/비공개입니다. 필터 변경은 기존 preview와 확인 상태를 무효화합니다. 요청 중 필터를 잠가 결과와 화면의 scope가 섞이지 않게 합니다.
- decision/issue/source-rights 표시: 대상 수, RELEASABLE/MANUAL/BLOCKED, duplicate 수, 권리 상태 분포, 생성 시각, digest/gateVersion을 표시합니다. UNKNOWN/미등록 source의 공개 차단을 설명하고 기존 Gate가 제공하는 code/classification/message로 issue를 표시합니다.
- sample UX: general/blocked/manual sample에 ID·표현/패턴·JLPT·decision·issue·source/rights와 관리자 상세 링크를 표시합니다. 사용자 데이터는 textContent/Thymeleaf escaping으로 출력합니다.
- execution confirmation: 기본 RELEASABLE_ONLY, 필수 사유와 대상 확인 checkbox를 요구합니다. 정확한 preview targetIds/digest/gateVersion을 기존 실행 API로 전달하며 filter 재검색은 하지 않습니다. 대상 수/공개 예정 수/override 수를 확인 영역에 표시합니다.
- manual override UX: 고급 mode에서 각 manual 대상의 모든 issue를 개별 체크하고 개별 사유를 입력합니다. 샘플 10건 제한과 별개로 전체 manual 대상을 기존 평가 결과에서 DTO에 제공하며 blocker에는 override UI가 없습니다. 일괄 승인 shortcut은 없습니다.
- stale preview 처리: STALE_PREVIEW/GATE_VERSION_MISMATCH/대상 변경을 새 Dry-run 필요 메시지로 표시하고 preview를 무효화합니다. 자동 재실행·공개는 없습니다. 동기 guard와 disabled/aria-busy 상태로 중복 제출을 방지합니다.
- batch history/detail: 최신순 20건 DB pagination과 reviewer fetch로 이력만 조회합니다. 상세에서 메타데이터/실행 요약/접기 가능한 전체 before-after manifest/override/source snapshot을 표시합니다.
- rollback UX: EXECUTED에만 사유 및 확인 checkbox가 있는 action을 제공합니다. 공개·검토 상태만 복원하고 학습 기록을 보존함을 설명합니다. ROLLED_BACK에는 action이 없고 conflict는 전체 거부 메시지로 안내하며 강제 rollback은 제공하지 않습니다.
- accessibility/responsive: form label, checkbox label, required, 상태 텍스트, live status/error와 오류 focus, keyboard focus outline, disabled/loading, table region 가로 스크롤을 적용했습니다. Chrome device metrics로 실제 1024px/390px를 지정하여 공통 CSS 포함 페이지 overflow와 synthetic 실행 흐름을 검증했습니다.
- backend 변경 범위: DTO에 전체 manual 대상/issue 설명 추가, 20건 이력 read service/repository, 관리자 page controller만 보완했습니다. Gate/source rights/digest/publication/override/rollback 정책과 V1~V4 migration은 변경하지 않았습니다.
- Master Matrix: A~F infrastructure 완료와 미커밋 상태만 갱신했습니다. 1A-1은 PARTIALLY RESOLVED이며 125개 feature row 상태/총계는 변경하지 않았습니다.
- 전체 테스트: `.\gradlew.bat test` 성공. XML 집계 180 tests / 0 failures / 0 errors / 1 skipped. 기존 A~E/학습/Flyway 회귀 포함. 추가 Java 3건은 관리자 접근·안전 기본값, synthetic 이력/manifest/rollback action, 샘플 제한 밖 전체 manual DTO와 기존 digest 일치를 검증합니다.
- JavaScript 테스트: `node --test src/test/js/admin-release.test.cjs` 9건 통과. exact payload, blocker/manual 차단, 사유·확인 필수, stale/conflict 문구, double submit을 검증했습니다.
- 브라우저 테스트: `node src/test/js/admin-release.browser.cjs` Chrome 1024px/390px 통과. synthetic HTTP 응답만 사용하며 실제 Spring/MySQL에 연결하지 않습니다. filter/result, issue/rights/sample escaping, manual checkbox/reason, exact request identity, 중복 요청 차단, stale 안내, blocker override 부재, 필터 변경 무효화 및 overflow를 확인했습니다.
- git diff --check: 통과(exit code 0).
- 실제 batch 실행 여부: 테스트 H2 synthetic 콘텐츠와 브라우저 mock 응답만 사용했습니다. 실제 JLPT imported content approve/publish/rollback 및 source rights 변경은 수행하지 않았습니다.
- 남은 위험/후속 작업: 실제 source license verification, actual N5 dry-run, duplicate adjudication, production-like rehearsal, N5 Word/Grammar pilot이 남아 있습니다. 아주 큰 manual 집합과 전체 상세 manifest 렌더링의 운영 규모 성능 검증도 필요합니다. 다음 Phase 1A 운영 검증 단계는 시작 가능하나 이 작업만으로 실제 공개가 승인된 것은 아닙니다.
- commit/push: 수행하지 않았습니다. A~F 변경사항을 working tree에 유지했습니다.

## 2026-09-16  JLPT-MAX Full Non-Audio Extraction / Staging

- `collection.anki21`만 ZIP에서 임시 파일로 복사해 직접 inventory했습니다. 20,650 notes / 38,967 cards / note type 4종(어휘 9,160, 문법 3,605, 어휘문제 7,876, 참조표 9). card 없는 어휘 note 1개, 여러 card를 가진 note 9,159개. Basic 모델은 note 0개입니다.
- V5 H2/MySQL additive `private_apkg_notes` 테이블: sourceRef+sourceNoteId unique, model ID/이름, GUID, 버전·파일, deck paths, card IDs/ord, tags, 순서 고정 raw field names/values, normalized JSON, audio reference count, 추출시각. ContentItem/기존 ImportedSourceRecord와 연결하지 않습니다. media/audio 전용 필드 값과 `[sound:...]`는 staging에 저장하지 않으며 binary를 읽지 않습니다.
- 명시적 `PrivateApkgExtractor.extract(path, sourceRef)`만 제공하며 startup runner/admin 공개 동작은 추가하지 않았습니다. SQLite ordered cursor와 100건 JDBC transaction batch로 추출합니다. 기존 source identity는 재실행에서 skip, 충돌 GUID/model은 오류 처리합니다.
- 실제 APKG 추출은 독립적인 in-memory H2(V1~V5)에서만 수행했습니다. 20,650 inserted, 0 malformed, raw fields 730,450, audio-reference-containing notes 17,036(메타데이터 수치만), 재실행 inserted 0 / skipped 20,650. category: vocabulary 9,160, grammar 1,078, practice 215, comprehensive 10,188(문법 model 2,527 + 어휘문제 model 7,661), reference 9, other 0. 종합 실전은 별도 model이 아닌 deck 경로로 구분합니다. local persistent DB와 production DB에는 추출하지 않았습니다.
- 기존 production importer는 어휘 Word/Reading/Meaning/PartOfSpeech/PitchAccent/ExamplesRendered/WordJLPT, 문법 FrontHTML/BackHTML/Kind/UnitID/Level을 변환해 사용합니다. 신규 staging은 누락됐던 VocabularyContext, MeaningV2, ExamplesV2, KanjiDetails, UsageDetails, RelatedWords, 기타 어휘 비오디오 원본 필드와 QuestionType/Prompt/ChoicesHTML/ExplanationHTML/ruby 및 참조표 TableHTML, card/deck 관계를 보존합니다. audio 관련 필드 이름만 남기고 값은 비웁니다.
- 기존 importer의 9,159 어휘 대상과 APKG 9,160 어휘 note의 차이는 card가 없고 `jlpt-max-vocabulary-retired` tag가 달린 note ID 1788408384419 1개입니다(기존 운영 DB row 수 및 실제 source ID 매칭은 별도 확인 필요). 문법 3,605 note 중 2,527은 종합 실전 deck에 배치되어 있으며 같은 model입니다. production content, source rights, review/published 및 학습 데이터는 이번 extraction 코드가 접근·수정하지 않습니다.
- 검증: synthetic fixture와 V4→V5/repeated migration, 실제 APKG H2 extraction/re-run, 전체 Gradle test 및 git diff --check. 영구 DB 적용/원본 재가공 품질 심사/라이선스 확인은 후속 작업입니다. commit/push 하지 않습니다.

## 2026-09-16  JLPT-MAX Ticket 1 / Ticket 1.5 — Real APKG Profiling & Staging Hardening

- Ticket 1(실제 APKG profiling)을 완료했습니다. 실제 소스 `JLPT-MAX-Deck-2.1.1.apkg`(리포지토리 루트, `.gitignore`로 제외)를 독립 in-memory H2로 추출해 실측했습니다: 20,650 notes / 38,967 cards / multi-card notes 9,159, category VOCABULARY 9,160·GRAMMAR 1,078·PRACTICE 215·COMPREHENSIVE 10,188·REFERENCE 9. 오디오/미디어 binary는 열거나 저장하지 않았습니다(collection.anki21 SQLite만 읽음).
- profiling 과정에서 audio isolation 결함을 발견했습니다: `[sound:...]` 브래킷 표기는 제거되지만 `<audio src="...">` 형태의 HTML5 audio 태그는 제거되지 않고 raw `field_values`에 파일명 텍스트로 남아 있었습니다(9,168 notes: Vocabulary `ExamplesRendered` 9,159건 + Reference `TableHTML` 9건). 오디오 바이너리 자체는 이번에도 열리지 않았습니다.
- Ticket 1.5에서 `PrivateApkgExtractor`를 최소 변경으로 강화했습니다. 기존 `[sound:...]` 제거는 유지하고, `<audio ...>` 태그(이중/단일 따옴표, 대소문자, 추가 attribute, 여러 줄)를 정규식 기반으로 정밀 제거하는 로직을 추가했습니다. 최초 구현은 Jsoup DOM 트리 제거 방식이었으나, 닫는 태그가 없는 `<audio>`가 뒤따르는 형제 콘텐츠 전체를 자식으로 삼켜버리는 위험을 회귀 테스트로 발견해, 태그 경계만 매칭하고 여는 태그에 즉시 인접한 닫는 태그만 선택적으로 제거하는 정규식 방식으로 교체했습니다(형제 콘텐츠를 건드릴 구조적 위험 없음). `isAudioField()` 기반 media 전용 field 정책은 그대로 유지했고, 일반 텍스트의 우연한 ".mp3" 문자열은 대상으로 삼지 않았습니다.
- `PrivateApkgExtractorTest`에 회귀 테스트를 추가했습니다: `<audio>` 변형(이중/단일 quote, controls/추가 attribute, 대소문자, 여러 줄, 닫는 태그 유무) 제거, 주변 ruby/div/span/table 및 한국어/일본어 텍스트 보존, 실제 `private_apkg_notes.field_values` DB 조회로 검증, idempotency 유지, production 테이블 미변경을 모두 확인합니다.
- 실제 APKG로 재실행한 결과 residue가 9,168 → **0**으로 줄었습니다. notes/cards/category 분포는 20,650/38,967/동일(카테고리 불변) — 추출 결과 자체는 바뀌지 않고 audio 잔재만 제거됨을 확인했습니다. Grammar 관련 수치(fallback 2,527건 등)는 변화 없음 — 애초에 Grammar 필드에는 audio 태그가 없었기 때문입니다.
- `JlptMaxStagingProfilingReport`(test-scope profiling 도구)를 정리했습니다: 세션별 하드코딩 절대경로를 제거하고 `build/reports/jlpt-max-profiling/`(module 상대경로) 출력으로 변경했습니다. `-Djapanese.actual-apkg=<path>` 명시적 opt-in 없이는 스킵되어 일반 Gradle test suite/CI를 깨지 않습니다(전체 suite 186 tests, 0 failures, 0 errors, 3 skipped 확인). production DB/media binary는 여전히 사용하지 않습니다. profiling 로직은 production service로 승격하지 않았습니다.
- 발견되었으나 이번 Ticket 범위 밖으로 후속 Ticket에 분리한 사항(수정하지 않음, 기록만): 정규(비-종합실전) GRAMMAR pattern 1,078/1,078건이 production `Grammar.pattern`(200자) 제한을 초과, `Grammar.explanation`(2000자) 제한 초과 667건, COMPREHENSIVE 문법 2,527건에서 기존 `GrammarHtmlParser`가 fallback(예문 0개), PRACTICE/COMPREHENSIVE의 실제 유효 콘텐츠는 `ChoicesHTML`/`ChoicesRubyHTML`/`*Ruby*` 계열 필드에 있고 기존 정규화 설계 문서가 참조한 필드는 대부분 placeholder.
- 검증: `PrivateApkgExtractorTest`/`FlywayMigrationTest` 개별 실행, 전체 Gradle test(186/0/0/3), 실제 APKG 재실행, `git diff --check`(exit 0) 모두 통과. Grammar/Vocabulary normalization parser, schema 변경, migration, candidate table, dedup 정책, source rights/publication, audio 재생/추출은 이번 Ticket에서 구현하지 않았습니다. commit/push는 수행하지 않았습니다.

## 2026-09-16  JLPT-MAX Ticket 2 — Vocabulary Normalization Parser

- `private_apkg_notes`의 VOCABULARY raw fields를 순수 함수로 정규화하는 `VocabularyNormalizationParser`(및
  `VocabularyNormalizationResult`/`NormalizedMeaning`/`NormalizedExample`/`NormalizedPitchAccent`/
  `NormalizedJlptLevel`/`VocabularyNormalizationIssue`/`VocabularyNormalizationSeverity`/
  `VocabularyNormalizationWarning`)를 구현 완료했습니다. production entity(ContentItem/Word/Meaning/
  Example)와 완전히 분리된 immutable result 구조이며, repository/EntityManager/JDBC/transaction 등
  DB/side effect가 전혀 없습니다. candidate DB 저장, ContentItem 생성, Release Gate 연동은 이번
  Ticket 범위 밖으로 구현하지 않았습니다.
- 실제 `JLPT-MAX-Deck-2.1.1.apkg` VOCABULARY 9,160건을 전수 검증했습니다: invalid/fatal candidate
  1건(기존 retired/card-less note와 동일 note), UNKNOWN_EXTRA_FIELD 0건, AUDIO_REFERENCE_UNEXPECTED
  0건. vocabulary field schema는 총 59개(PRIMARY 9 / SENTINEL 40 / KNOWN_EXTRA 10, 교집합 0, 미분류
  0)로 완전히 분류됩니다. EntryID duplicate 0건. production/migration/source rights/publication에는
  영향이 없습니다.
- 검증: 신규 `VocabularyNormalizationParserTest`(33건) 전부 pass, 전체 Gradle test 220건(0 failures,
  0 errors, 4 skipped) 통과. 독립 READ-ONLY 코드 리뷰 결과 PASS WITH MINOR(BLOCKER/MAJOR 없음).
- 후속 NOTE로만 기록(이번 Ticket에서 수정하지 않음): `validForPromotion`은 FATAL 경고 부재 신호일
  뿐 publication 결정이 아니며, 향후 candidate storage 티켓에서 명명 재검토 가능. `KNOWN_EXTRA_FIELDS`
  (KanjiDetails 등)의 HTML 구조는 현재 텍스트 평탄화만 수행하며 구조적 파싱은 후속 과제. 기존
  `ApkgVocabularyImporter`의 반복 meaning separator(`A / / B`) 처리 개선은 별도 후속 Ticket으로 분리
  가능.
