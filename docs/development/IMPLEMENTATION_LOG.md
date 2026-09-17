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
- 발견되었으나 이번 Ticket 범위 밖으로 후속 Ticket에 분리한 사항(수정하지 않음, 기록만): 정규(비-종합실전) GRAMMAR pattern 1,078/1,078건이 production `Grammar.pattern`(200자) 제한을 초과 [**Ticket 3A.1(2026-09-16) note-level 실측으로 정정: 1,078건이라는 measurement count 자체는 정확했으나, 이 1,078건을 "정규 GRAMMAR"로 귀속한 것이 틀렸음. 실제로는 COMPREHENSIVE의 `IsPassageBlank` subtype 1,078건이며(정규 GRAMMAR 1,078건과 우연히 같은 개수), 정규 GRAMMAR(`IsBasic`) 1,078건은 pattern max 26자·avg 7.3자로 200자 제한과 전혀 무관함이 확인됨. 상세: 아래 "JLPT-MAX Ticket 3A.1" 항목 및 `docs/development/JLPT_MAX_TICKET3A1_GRAMMAR_PROFILING_2026-09-16.txt` 참고.**], `Grammar.explanation`(2000자) 제한 초과 667건 [**Ticket 3A.1 정정: 이 667건도 전부 COMPREHENSIVE/`IsPassageBlank`이며 100% `back.text()` fallback 경로에서만 발생. 정규 GRAMMAR(`IsBasic`)는 667건 중 0건 — structured explanation 추출 성공률 1,078/1,078(100%), >2000 0건.**], COMPREHENSIVE 문법 2,527건에서 기존 `GrammarHtmlParser`가 fallback(예문 0개), PRACTICE/COMPREHENSIVE의 실제 유효 콘텐츠는 `ChoicesHTML`/`ChoicesRubyHTML`/`*Ruby*` 계열 필드에 있고 기존 정규화 설계 문서가 참조한 필드는 대부분 placeholder.
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

## 2026-09-16  JLPT-MAX Ticket 3A.1 — Grammar Profiling Utility Extension (actual APKG 실측 + self-review)

- 작업 목적: Ticket 3B(GrammarNormalizationParser)를 설계하기 전에, Grammar-model(note_type
  `JLPT MAX덱 문법`, 3,605건)의 Is* subtype 플래그, `Kind` 필드, FrontHTML/BackHTML 실제 구조를
  note 단위로 실측하는 test/dev-scope profiling 도구를 만들고, 실제 `JLPT-MAX-Deck-2.1.1.apkg`로
  검증했다. production 코드/스키마/데이터는 이번 Ticket에서 변경하지 않았다.
- 신규 test-scope 도구: `GrammarNormalizationProfilingReport`(opt-in `-Djapanese.actual-apkg=<path>`,
  일반 suite/CI는 항상 skip)와 `GrammarNormalizationProfilingReportSyntheticTest`(합성 fixture로
  로직을 상시 검증). `PrivateApkgExtractor`/`GrammarHtmlParser`는 수정하지 않았다.
- actual APKG 검증: 파일 크기 1,148,891,855 bytes, SHA-256
  `9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d` — 공식 v2.1.1 release와
  byte-for-byte 일치 확인 후 profiling을 실행했다(GitHub 공식 release에서 세션 내 다운로드,
  repository에는 추가하지 않음, `.gitignore`의 `*.apkg` 규칙대로 미추적).
- **Is* subtype partition(note-level 확정)**: 3,605건 전부가 4개 flag 중 **정확히 하나**에만
  속하며, 동시 활성화(multi-flag)나 미분류(unclassified)는 0건이다: `GRAMMAR`/`IsBasic`=1,078,
  `COMPREHENSIVE`/`IsGrammarForm`=1,078, `COMPREHENSIVE`/`IsPassageBlank`=1,078,
  `COMPREHENSIVE`/`IsSentenceArrangement`=371. 이는 세 개의 독립 boolean이 우연히 같은 note에서
  같이 켜지는 것이 아니라, 서로 배타적인 4-way subtype selector임을 의미한다.
- **`Kind` 필드**: 3,605/3,605건 전부 raw staging 값이 U+2063(INVISIBLE SEPARATOR, length=1)
  placeholder이며, subtype/category/Level에 무관하게 동일하다. `AnkiFieldTextNormalizer` 적용 시
  이미 `null`로 정규화된다(`private_apkg_notes.normalized_values`의 `"connection":null`로 직접
  확인). 즉 `Kind`는 실제 문법 접속(connection) 정보를 전혀 담고 있지 않다 — 향후 Ticket 3B-1
  설계에서 `Kind`를 `Grammar.connection`으로 매핑하면 안 된다.
- **pattern>200 정정 (중요, 기존 기록 오류 수정)**: 기존 Ticket 1 기록의 "정규 GRAMMAR 1,078/
  1,078건이 pattern>200 초과"는 **measurement count(1,078)는 맞았지만 category/subtype 귀속이
  틀렸다**. note-level 재집계 결과 pattern>200은 `COMPREHENSIVE`/`IsPassageBlank`의 1,078건과
  정확히 일치하며(총 1,078건 중 1,078건 모두 초과, max 745자), `GRAMMAR`/`IsBasic`은 1,078건 중
  0건(max 26자), `COMPREHENSIVE`/`IsGrammarForm`은 1,078건 중 0건(max 82자),
  `COMPREHENSIVE`/`IsSentenceArrangement`는 371건 중 0건(max 46자)이다. 두 그룹이 우연히 같은
  개수(1,078)였기 때문에 category 분리 없이 측정했던 기존 도구가 잘못 귀속한 것으로 판단된다.
- **explanation>2000 정정**: 667건 전부 `COMPREHENSIVE`/`IsPassageBlank`이며 100%
  `back.text()` fallback 경로에서만 발생한다(구조화 `div._j4z` 추출로 초과한 건 0건). `GRAMMAR`/
  `IsBasic`은 667건 중 0건 — **[Ticket 3B-1(2026-09-17) 정정: 여기서 "structured explanation
  추출 성공률 1,078/1,078(100%)"이라고 쓴 것은 selector 적중 횟수 자체는 맞았으나 semantic
  귀속이 틀렸다. `div._j4z`는 grammar explanation이 아니라 FrontHTML 예문 문장의 한국어
  번역문이다. 상세: 아래 "JLPT-MAX Ticket 3B-1" 항목 참고.]**. 즉 이 문제는
  `Grammar.explanation` 컬럼 크기 문제가 아니라, 지문형(passage-length) `IsPassageBlank` 콘텐츠에
  Grammar용 explanation selector를 잘못 적용한 결과다.
- **정규 GRAMMAR(`IsBasic`, 1,078건) 안정성**: `<mark>` selector 적중률 1,078/1,078(100%,
  fallback 0), pattern max 26자·avg 7.3자(200자 제한과 무관), structured explanation 추출
  1,078/1,078(100%, >2000 0건), example 개수 정확히 2개/노트(zeroExampleNotes 0, 분산 없음)
  **[Ticket 3B-1 정정: 이 두 수치("structured explanation 추출 100%", "example 정확히 2개")는
  count 자체는 정확했으나 semantic label이 틀렸다. 실제로는 `div._j4z`(front 예문 번역문) 추출
  성공률과, `section._j4a`의 "뉘앙스"+"접속" 참고 카드 2개가 우연히 기존 example 필터 조건을
  만족해 "example"로 잘못 인식된 것이었다. 세 번째 카드("헷갈리는 문형")는 구조가 달라(div._j4v
  대신 ul 리스트) 항상 필터에서 탈락했기 때문에 "언제나 정확히 2개"라는 안정적 착시가 생겼다.
  실제 semantic model은 pattern/frontExample(일본어+번역)/meaningGloss/nuance/connection/
  confusablePatterns 6개이며 "explanation"/"examples" 개념은 존재하지 않는다. 상세: 아래
  "JLPT-MAX Ticket 3B-1" 항목 참고.]**. 독립적으로 재작성한 self-review 스크립트로도 동일
  count가 재현되어, Ticket 3B-1(정규 GRAMMAR pure parser)은 착수해도 안전하다고 판단했다 — 이
  결론(3B-1 착수 가능) 자체는 Ticket 3B-1에서도 유지되었다.
- **COMPREHENSIVE(2,527건) 성격**: 구조 분석 결과 한국어 지시문 + 일본어 지문(blank 포함) +
  한국어 번역 + 4지선다 `<ol>`(`li.is-correct`로 정답 표시) 형태로, `div._j4z`/`section._j4a`
  적중률이 0/2,527이다 — Grammar 지식 콘텐츠(pattern/explanation/example)가 아니라
  Practice/Question 콘텐츠로 판단된다. `IsSentenceArrangement`(371건)는 `<ol>`/`<li>` 없이
  `span.star-piece` 구조를 쓰는 별도 형태로, 나머지 두 subtype과도 다른 전용 parser가 필요하다.
- 검증: `GrammarNormalizationProfilingReportSyntheticTest` 통과, actual APKG opt-in profiling
  실행 성공(`build/reports/jlpt-max-profiling/grammar-profiling.txt`), self-review용 임시
  스크립트로 note-ID 단위 교차검증 후 즉시 삭제(저장소에 흔적 없음), 전체 `./gradlew test` 222
  tests/1 failed(기존 `LearningOrganizationServiceTest`, 별도 isolated worktree로 remote baseline
  fcb2820에서도 동일 재현 확인 — Ticket 3A.1과 무관한 pre-existing 이슈)/5 skipped, `git diff
  --check` 통과.
- 상세 실측 수치와 DOM skeleton 샘플은
  `docs/development/JLPT_MAX_TICKET3A1_GRAMMAR_PROFILING_2026-09-16.txt` 참고.
  GrammarNormalizationParser/Practice parser 구현, production Grammar/GrammarHtmlParser 수정,
  migration, schema 변경, source rights/publication 변경은 이번 Ticket에서 수행하지 않았다.
- 남은 후속 과제: Ticket 3B-1(정규 GRAMMAR pure parser)은 착수 가능. COMPREHENSIVE는
  Grammar entity 확장이 아니라 별도 Practice/Question 도메인 모델로 설계해야 하며,
  `IsGrammarForm`/`IsPassageBlank`(4지선다)와 `IsSentenceArrangement`(단어배열)는 서로 다른
  parser가 필요하다 — 각각 후속 Ticket으로 분리 권장.

## 2026-09-17  JLPT-MAX Ticket 3B-1 — Grammar Normalization Parser (semantic model 재정정 포함)

- 작업 목적: `category=GRAMMAR`/`IsBasic` active(1,078건)를 대상으로 pure Grammar
  normalization parser를 구현했다. actual `JLPT-MAX-Deck-2.1.1.apkg`(size
  1,148,891,855 bytes, SHA-256 `9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d`,
  기존 Ticket 3A.1과 동일 파일 재확인)로 검증했다. COMPREHENSIVE(2,527건)는 이번 Ticket
  대상이 아니다.
- **BackHTML semantic model 재정정(중요)**: 구현 중 `section._j4a`를 "example"로 다루던
  기존 해석이 실제로는 틀렸음을 발견했고, 전체 1,078건 재실측(예외 0건)으로 확정했다.
  기존 Ticket 1/3A.1 기록의 "structured explanation 100%", "example 정확히 2개"는 selector
  적중 count 자체는 맞았으나 semantic 귀속이 틀렸다(위 Ticket 1/3A.1 항목에 correction
  추가함). 실제 구조:
  - `div._j4z` = FrontHTML 예문 문장의 한국어 번역 (explanation 아님)
  - `div._j4x` = 문법 의미 gloss (예: "저~") — 이전까지 아무도 추출하지 않던 필드
  - `section._j4a`는 항상 정확히 3개, label 고정("뉘앙스"/"접속"/"헷갈리는 문형") —
    bilingual example이 아니라 Nuance/Connection/Confusable-patterns 참고 카드다.
    "접속"(Connection) 카드의 `div._j4v`가 실제 grammar connection 정보이며, `Kind`
    필드(U+2063 placeholder, 항상 비어있음)는 connection source가 아니다.
- **최종 normalized 필드**(`GrammarNormalizationResult`): sourceRef, sourceNoteId, unitId,
  pattern, frontExample(japaneseText/reading/translation), meaningGloss, nuance,
  connection, confusablePatterns(List), level, rawKind, unknownFields, warnings,
  hasNoFatalIssues. `explanation`/`examples` 필드는 없다 — production `Grammar`
  entity(pattern/explanation/connection)에 강제로 맞추지 않았고, explanation에 무엇을
  매핑할지는 별도 promotion Ticket의 결정 사항으로 남겼다.
- **frontExample**: FrontHTML `div._j4u`(mark 포함 전체 예문 문장)에서 추출, reading은
  v2.1.1에서 항상 null(front 예문에 ruby 0/1,078). translation은 `div._j4z`. 전체
  1,078건에서 FrontHTML `div._j4u`와 BackHTML 자체 사본 `div._j4u`의 ruby-stripped
  텍스트가 100% 정확히 일치함을 확인했다 — 이전 발견("front/back mark 텍스트 71% 불일치")은
  콘텐츠 차이가 아니라 순수 furigana 렌더링 차이였음이 이번에 확정됐다.
- **subtype guard**: `COMPREHENSIVE`/`IsGrammarForm`/`IsPassageBlank`/`IsSentenceArrangement`는
  `UNSUPPORTED_SUBTYPE`(FATAL)로 즉시 반환하고 content 필드는 채우지 않는다. `IsBasic`과
  다른 subtype flag가 동시에 켜지면 subtype identity 자체가 불확실해지므로
  `CONFLICTING_SUBTYPE_FLAGS`를 FATAL로 판정한다(content는 참고용으로 계속 추출).
- **silent-loss guard**: 헷갈리는 문형 카드의 `li._j1f`에 알려진 pattern/explanation
  span 외 세 번째 이상의 직계 `<span>`이 있으면, 값은 알려진 두 필드만 보존하되
  `MALFORMED_CONFUSABLE_PATTERN_ENTRY`(REVIEW_REQUIRED)로 플래그해 미지의 source
  정보가 조용히 유실되지 않게 했다.
- **severity**: FATAL = UNSUPPORTED_SUBTYPE, CONFLICTING_SUBTYPE_FLAGS, MISSING_UNIT_ID,
  MISSING_PATTERN, MISSING_FRONT_EXAMPLE, MISSING_FRONT_TRANSLATION, MISSING_MEANING_GLOSS,
  MISSING_NUANCE, MISSING_CONNECTION, MISSING/INVALID_JLPT_LEVEL. REVIEW_REQUIRED/
  INFORMATIONAL은 fallback/count-mismatch/label-mismatch/malformed-entry/unexpected-Kind/
  audio-residue/unknown-field 등. provenance(`sourceRef`/`sourceNoteId`)는
  `VocabularyNormalizationParser`와 동일하게 프로그래머 계약 위반으로
  `IllegalArgumentException`을 던진다.
- **legacy `GrammarHtmlParser`**: 수정하지 않았다. production import 경로(pattern/
  explanation/connection 200/2000/500자 silent truncation 포함)는 이번 Ticket과 무관하게
  그대로 유지된다 — semantic이 틀렸다는 사실만 문서화했다.
- 검증: `GrammarNormalizationParserTest` 35건 전부 pass. actual APKG 1,078건 전수
  실행 결과 fatal 0 / review-required 0 / 모든 issue count 0(완전히 clean),
  max pattern 26자, max meaningGloss 36자, max connection 95자, confusablePatterns
  count != 3인 note 0건. 전체 `./gradlew test` 259 tests, 0 failures, 0 errors, 7
  skipped, `git diff --check` 통과. `LearningOrganizationServiceTest`는 이번 실행에서
  통과했다(2026-09-16 세션에서는 baseline 포함 일관되게 실패 → 2026-09-17에는 코드 변경
  없이 통과 — 날짜 의존적 flaky 가능성, 이번 Ticket과 무관, 별도 조사 필요).
- candidate 영속화, dedup, source rights/publication 연동, production `Grammar`
  스키마 매핑은 이번 Ticket 범위 밖이며 후속 promotion Ticket에서 결정한다.
  `GrammarEnrichment`/`GrammarRelation`/`GrammarComparison`은 생성/수정하지 않았고,
  `nuance`/`confusablePatterns`를 이 도메인들에 자동 매핑하지 않았다(이름이 비슷하다는
  이유만으로 curated/reviewed 도메인에 편입시키지 않음). migration/schema/production
  data 변경 없음. commit은 이 로그 항목과 함께 별도로 수행한다.

## 2026-09-17  JLPT-MAX Ticket 4A — Normalized Content Candidate 영속화 계층

- 작업 목적: 순수 in-memory `VocabularyNormalizationResult`/`GrammarNormalizationResult`
  (Ticket 2/3B-1, 이번 Ticket에서 미수정)와 아직 구현되지 않은 dedup(4B)/review(4C)/
  promotion 단계 사이에, 독립적으로 영속화되는 private candidate snapshot 계층을
  추가했다. raw staging(`private_apkg_notes`) / 순수 normalization 결과 / 이번
  Ticket의 candidate 스냅샷 / production(`ContentItem`/`Word`/`Grammar`) 4개 계층을
  섞지 않았고, `ImportedSourceRecord`(production-`ContentItem` 연결 가능 구조)는
  재사용하지 않았다.
- **신규 엔티티**(`com.japanese.content.entity`, 8개 + enum 2개): `NormalizedContentCandidate`
  (envelope: candidateType/sourceRef/sourceNoteId/sourceIdentityKey/qualityState/
  normalizedAt), `NormalizedCandidateType`(VOCABULARY, GRAMMAR — 향후 도메인 확장을
  전제로 하되 오늘 필요한 2종만), `NormalizedCandidateQualityState`(FATAL >
  REVIEW_REQUIRED > INFORMATIONAL > CLEAN, worst-wins, `validForPromotion`/
  `hasNoFatalIssues`를 approval 상태로 재라벨링하지 않음), `NormalizedCandidateWarning`/
  `NormalizedCandidateExtraField`(두 타입이 공유하는 자식 테이블), Vocabulary 전용
  `NormalizedVocabularyCandidateDetail`/`...Meaning`/`...Example`, Grammar 전용
  `NormalizedGrammarCandidateDetail`/`...ConfusablePattern`. JSON blob이 아닌 구조적
  relational 자식 테이블로 warnings/meanings/examples/confusablePatterns/extras를
  저장했다(추후 4B/4C에서 쿼리 가능하도록).
- **identity 설계**: `(source_ref, source_note_id, candidate_type)`가 정확히 하나의
  현재 스냅샷을 나타내는 unique 제약이다. `source_identity_key`(Vocabulary의 EntryID,
  Grammar의 UnitID)는 **unique 제약을 의도적으로 부여하지 않았다** — dedup은 Ticket
  4B의 몫이다. raw staging과의 연결은 논리적 참조만 사용했다(`source_ref`+
  `source_note_id`, FK 없음) — `private_apkg_notes`는 이 코드베이스 전체에서 JDBC 전용
  raw 테이블로 취급되고 JPA 엔티티가 존재하지 않아, 이 테이블만을 위한 첫 JPA 엔티티를
  새로 만들지 않기로 했고, candidate가 staging row와 강한 cascade-delete로 묶이지
  않도록 하기 위함이다.
- **FATAL 영속화**: 모든 semantic 컬럼을 nullable로 설계해 FATAL candidate(예:
  EntryID/Word/Reading 전부 누락)도 그대로 저장된다 — 필터링하거나 드롭하지 않는다.
  실제 APKG 검증에서 Vocabulary FATAL 1건이 정확히 저장됨을 확인했다(아래 참고).
- **Migration**: `V6__add_normalized_content_candidates.sql`(H2/MySQL 동일 의미,
  V1-V5 무수정)로 8개 테이블을 추가했다:
  `normalized_content_candidates`, `normalized_candidate_warnings`,
  `normalized_candidate_extra_fields`, `normalized_vocabulary_candidates`,
  `normalized_vocabulary_candidate_meanings`, `normalized_vocabulary_candidate_examples`,
  `normalized_grammar_candidates`, `normalized_grammar_candidate_confusable_patterns`.
  `FlywayMigrationTest`에 V6 fresh-DB/V5→V6 upgrade/MySQL-additive 테스트를 추가하고,
  기존 migrationsExecuted 기대값(V1-V5 → V1-V6)을 갱신했다.
- **`NormalizedCandidateStore`**(`com.japanese.content.service`): `saveVocabulary`/
  `saveGrammar` 두 메서드로 Result → entity 매핑을 결정적으로 수행한다. 파서는
  전혀 수정하지 않았고, sourceRef/sourceNoteId는 오직 Result 자체에서만 가져온다.
  재저장(refresh) 시에는 기존 envelope을 찾아 자식 테이블들을 **즉시 실행되는 벌크
  JPQL delete**로 먼저 제거하고(`entityManager.createQuery("delete from ... where
  candidate.id = :id")`), `flush()+clear()`로 stale 1차 캐시를 비운 뒤 candidate를
  다시 로드해 새 자식들을 채운다 — 처음에는 엔티티 컬렉션의 `clear()` + `orphanRemoval`
  방식으로 구현했으나, 두 번째 저장 시 새 행의 INSERT가 기존 행의 DELETE보다 먼저
  flush되어 `(candidate_id, order)` unique 제약을 위반하는 실패를 실제로 재현했고
  (`doubleSaveOfIdenticalResultDoesNotDuplicate`/`refreshReplacesSnapshotWithoutStaleChildRows`
  테스트가 최초 구현에서 실패), 벌크 delete + flush/clear 방식으로 교체해 해결했다.
  전체 저장은 하나의 `@Transactional` 메서드 안에서 원자적으로 수행된다.
- **Production isolation**: `saveVocabulary`/`saveGrammar`는 `ContentItem`/`Word`/
  `Grammar`/`GrammarEnrichment`/`GrammarRelation`/`GrammarComparison`을 생성·수정·
  참조하지 않고 `ImportedSourceRecord.linkContentItem`도 호출하지 않는다.
  `savingCandidatesNeverChangesProductionOrImportedSourceRowCounts` 테스트로 각
  repository의 count가 저장 전후 불변임을 확인했다.
- **테스트**(`NormalizedCandidateStoreTest`, `NormalizedCandidateStoreAtomicityTest`):
  clean/FATAL Vocabulary·Grammar 저장-조회 lossless 검증(meanings/examples/
  confusablePatterns/frontExample 순서 보존 포함), EntryID/UnitID가 unique하지
  않음(같은 키로 다른 note 2건 저장 성공), 동일 결과 두 번 저장 시 중복 없음, refresh 시
  stale child row 없음(직접 SQL count로 검증), production/ImportedSourceRecord row
  count 불변. Atomicity 테스트는 class-level `@Transactional`을 의도적으로 붙이지
  않고(테스트 트랜잭션 rollback에 편승하면 실제 원자성을 검증하지 못하므로) confusable
  pattern 2건에 동일 displayOrder를 주어 unique 제약 위반을 유도한 뒤, 예외 발생과
  envelope row 전체 rollback(0건)을 직접 SQL로 확인했다.
- **actual APKG 검증**(opt-in, `NormalizedCandidateStoreRealApkgReport`, 전체 test
  suite와 별도 Gradle 실행으로 OOM 회피): SHA-256 `9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d`
  재확인 후 VOCABULARY 9,160건(FATAL 1건 포함) + GRAMMAR 1,078건 = 총 10,238건 전수
  parse+save 성공, `repository.findByCandidateType(...)` count가 각각 저장 건수와
  정확히 일치함을 확인했다(FATAL 1건이 스킵되지 않고 저장됐음을 직접 검증).
- 검증: `NormalizedCandidateStoreTest` 8건 + `NormalizedCandidateStoreAtomicityTest`
  2건 전부 pass. 전체 `./gradlew test` 280 tests, 0 failures, 0 errors, 8 skipped
  (opt-in 7건 + 이번 Ticket opt-in 1건). `git diff --check`/`git diff --no-index --check`
  통과.
- dedup/conflict resolution, human review, promotion, production `Grammar`/`Word`
  매핑, source rights/publication 변경, Release Gate 변경, COMPREHENSIVE parser,
  Practice/Question 도메인, Admin UI, public API/컨트롤러/Thymeleaf 노출, 검색
  인덱싱은 전부 이번 Ticket 범위 밖이다.

### 2026-09-17 Ticket 4A commit 전 최종 리뷰(uncommitted 상태에서 진행)

- **HEAD SHA 오타 확인**: baseline HEAD는 `eeeacdb41763063c38a04794bd11830e81665475`
  이다(리뷰 시작 시 `git rev-parse HEAD`로 재확인). 이전 턴에서 채팅으로 전달한
  보고서 텍스트 파일(repo 밖, `/tmp` scratch 파일)에 `...bd11838e...`로 한 글자
  오타가 있었으나, repo 내 어떤 파일에도(이 로그 포함) 해당 SHA가 기록된 적이
  없음을 `grep`으로 확인했다 — 코드/history 수정 없음, repo 문서 수정도 불필요했다.
- **Word/Grammar production isolation 누락 보완**: 기존
  `savingCandidatesNeverChangesProductionOrImportedSourceRowCounts` 테스트는
  ContentItem/GrammarEnrichment/GrammarRelation/GrammarComparison/
  ImportedSourceRecord count만 확인하고 있었다(Word/Grammar는 전용 Repository가
  없어 누락돼 있었음). `words`/`grammars` 테이블에 대한 직접 `JdbcClient` count
  비교를 추가해 저장 전후 두 테이블도 불변임을 확인하도록 최소 수정했다.
- **refresh 실패 rollback 테스트 추가**: 기존 atomicity 테스트는 신규 candidate
  최초 저장 실패만 검증하고 있었다. 기존 스냅샷이 있는 상태에서 refresh(bulk
  delete → flush → clear → reload → 새 child insert) 도중 constraint violation이
  나는 경우를 별도로 검증하는
  `aFailedRefreshRollsBackAndPreservesThePreviousSnapshot` 테스트를 추가했다:
  정상 Grammar candidate 저장 → 동일 identity로 confusablePattern 2건에 동일
  displayOrder를 준 refresh 시도 → `DataIntegrityViolationException` 확인 →
  envelope/detail/confusablePattern/warning/extraField가 전부 refresh 이전
  값 그대로 유지됨을(신규 detached-lazy-collection 문제를 피하기 위해 직접 SQL로)
  확인했다. 실패한 새 스냅샷의 흔적은 전혀 남지 않았다.
- **QualityState ordinal 의존성 제거**: `NormalizedCandidateQualityState.worstOf`가
  `Comparator.naturalOrder()`(= enum 선언 순서/ordinal)에 의존하고 있어, 상수
  선언 순서를 실수로 바꾸면 worst-wins 의미가 조용히 달라질 수 있는 구조였다.
  각 상수에 명시적 `severityRank`(FATAL=0, REVIEW_REQUIRED=1, INFORMATIONAL=2,
  CLEAN=3) 정수 필드를 추가하고 `worstOf`가 이 값으로 비교하도록 최소 수정했다
  (별도 mapping 클래스/과도한 추상화 없이 enum 내부 필드만 추가). 신규
  `NormalizedCandidateQualityStateTest`(7건: 무경고→CLEAN, INFO단독,
  REVIEW_REQUIRED단독, FATAL단독, INFO+REVIEW_REQUIRED, REVIEW_REQUIRED+FATAL,
  INFO+FATAL)로 조합별 worst-wins 결과를 고정했다.
- **보고서 숫자 정정**: 채팅으로 전달했던 보고서의 "`NormalizedCandidateStoreTest`
  9건"은 실제로는 8건이었고(위에서 이미 정정), "신규 파일 16건"도 실제로는
  이 리뷰 이전 시점 기준 16건이 맞았으나 이번 리뷰에서 파일 2건
  (`NormalizedCandidateQualityStateTest.java`, 그리고 이전 턴에 이미 추가돼 있던
  `NormalizedCandidateStoreRealApkgReport.java`를 포함한 부록 목록 자체가 17개였음)
  이 추가/누락 집계돼 있었다. 리뷰 완료 시점 기준 정확한 수치는 최종 응답에
  기록한다.
- 이번 리뷰에서 `NormalizedCandidateStore.java`/엔티티의 저장 매핑 semantic은
  변경하지 않았다(quality state 계산 결과 자체는 동일, 내부 비교 방식만 변경).
  따라서 1.1GB 실제 APKG 전체 재검증은 다시 수행하지 않았다 — 이전 턴에
  VOCABULARY 9,160(FATAL 1건 포함)+GRAMMAR 1,078=10,238건 검증 결과가 그대로
  유효하다. commit/push는 이 리뷰 직후 별도로 수행한다.

### 2026-09-17 Ticket 4A independent review MINOR 2건 follow-up

- `NormalizedContentCandidate.attachVocabularyDetail`/`attachGrammarDetail`에
  candidateType guard(불일치 시 `IllegalStateException`)를 추가하고
  `NormalizedCandidateStoreRealApkgReport`의 known-good v2.1.1 absolute 수치
  (VOCABULARY 9,160/GRAMMAR 1,078/합계 10,238/Vocabulary FATAL 1/Grammar
  FATAL·REVIEW_REQUIRED 0)를 assertion으로 고정했다 — 둘 다 실제 APKG opt-in
  재실행으로 확인 후 고정.

## 2026-09-17  JLPT-MAX Ticket 4B — Normalized Candidate Dedup/Conflict Analysis

- 작업 목적: Ticket 4A가 영속화한 private `NormalizedContentCandidate` 스냅샷들
  사이의 중복/충돌을 탐지하고, 판정 결과와 근거(evidence)를 private 영역에
  저장한다. 자동 merge/삭제/canonical winner 선택, production matching,
  human review/approval 중 어느 것도 이번 Ticket 범위가 아니다 — 파이프라인은
  raw/private staging → pure normalization → private normalized candidate →
  **dedup/conflict detection(이번 Ticket)** → human review(4C, 미구현) →
  production promotion → publication 순서이며, 이번 Ticket은 굵게 표시한
  단계만 구현한다.
- **identity 3분법**: (A) provenance(`sourceRef`+`sourceNoteId` — 어디서
  왔는가), (B) source-native identity hint(Vocabulary `EntryID`/Grammar
  `UnitID` — source가 부여한 semantic identity 힌트), (C) global production
  content identity(아직 미결정)를 절대 혼동하지 않았다. `EntryID`/`UnitID`를
  전역 `Word`/`Grammar` identity와 동일시하는 코드는 어디에도 없다.
- **actual v2.1.1 profiling을 규칙 설계보다 먼저 수행**(`NormalizedCandidateConflictProfilingReport`,
  opt-in, `-Djapanese.actual-apkg=<path>`, SHA-256
  `9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d` 재확인):
  이전 Ticket 3A.1의 "duplicate normalized pattern ≈234 groups" 수치는 이번
  Ticket에서 그대로 재사용하지 않고 현재 persisted-candidate-equivalent 필드
  기준으로 처음부터 다시 계산했다(그 수치는 다른 profiling 목적/다른 필드
  기준이었을 가능성이 높다 — 실측 재계산이 이 차이를 그대로 드러냈다). 실측
  결과:
  - **Vocabulary(9,160건)**: non-null EntryID 9,160/9,160(100%), **duplicate
    EntryID groups = 0**(실제 데이터에는 EntryID 중복이 전혀 없다). 정규화
    expression+reading 중복 그룹은 1개(2건, だぶる/だぶる) 뿐이고, 이 한 쌍조차
    level(N2 vs N1)·meanings·EntryID가 전부 다르다 — 완전 동일(fully
    identical) 쌍은 0건. expression-only 중복 그룹 14개/reading-only 중복
    그룹 455개는 비교 대상에서 제외했다(아래 blocking 참고).
  - **Grammar(1,078건)**: non-null UnitID 1,078/1,078(100%), **duplicate
    UnitID groups = 0**(UnitID도 전혀 중복이 없다). 정규화 pattern 중복
    그룹은 9개(최대 그룹 크기 6, 예: で 패턴 6건), pattern 단독 pair는
    32쌍(전부 UnitID가 다름 — UnitID가 항상 유일하므로 당연함), 그중 30쌍은
    level까지 같음(pattern+level 그룹 7개). 이 30쌍 중
    meaningGloss/connection/nuance가 **완전히 동일한 쌍은 0건** — 즉 같은
    pattern(같은 level 포함)을 공유하는 실제 데이터는 예외 없이 서로 다른
    문법 항목(예: で=장소/도구/원인/이유 등 서로 다른 용법)이었다. 이 실측이
    "pattern을 identity로 취급하면 안 된다"는 Ticket 지침을 정량적으로
    뒷받침한다.
- **분석 상태 모델**(`NormalizedCandidateMatchAssessment`): `UNIQUE`(계산
  결과일 뿐 절대 행으로 저장되지 않음)/`EXACT_DUPLICATE`/
  `POSSIBLE_DUPLICATE`/`CONFLICT` 4종만 정의했다 — `INSUFFICIENT_DATA` 등
  추가 상태는 실제 필요성이 확인되지 않아 도입하지 않았다(FATAL candidate
  처리는 별도 항목 참고). `NormalizedCandidateQualityState`(completeness
  축)와는 완전히 분리된 별개의 axis이며 하나의 enum으로 합치지 않았다.
- **분류 규칙**(Vocabulary/Grammar 대칭 설계, `NormalizedCandidateConflictAnalyzer`):
  - CONFLICT: 같은 EntryID/UnitID(둘 다 present & 동일)를 주장하는데 핵심
    필드(Vocabulary는 expression 또는 reading, Grammar는 pattern)가 다른
    경우. 실제 데이터에는 EntryID/UnitID 중복이 없으므로 실제 v2.1.1
    결과에는 CONFLICT가 0건이고, synthetic 테스트(C/D/H)로만 재현된다.
  - 핵심 필드(Vocabulary: expression+reading 둘 다 동일, Grammar: pattern
    동일)가 일치할 때만 EXACT/POSSIBLE을 판정한다. **EntryID/UnitID가 둘 다
    present인데 서로 다르면("식별자 불일치") 다른 모든 필드가 완전히
    같더라도 EXACT_DUPLICATE로 격상시키지 않고 POSSIBLE_DUPLICATE로
    제한한다** — source가 명시적으로 다른 항목이라고 주장하는 이상 이
    Ticket이 그 주장을 EXACT로 덮어쓰지 않는다는 설계 결정이다(테스트
    `differentEntryIdCapsExactDuplicateEvenWithIdenticalContent`/`g_...`로
    고정). EntryID/UnitID가 없거나(FATAL 등) 한쪽만 있는 경우는 이 cap이
    적용되지 않는다 — 정보 부재는 "불일치"가 아니다.
  - 핵심 필드 일치 + 식별자 불일치가 아님 + 보조 필드(Vocabulary:
    meanings+level, Grammar: level+meaningGloss+connection+nuance)까지 전부
    같으면 EXACT_DUPLICATE, 하나라도 다르면 POSSIBLE_DUPLICATE.
  - expression-only/reading-only(Vocabulary) 단독 일치는 애초에 비교
    대상에 포함하지 않는다(아래 blocking) — Ticket 지침("같은 표기라도
    읽기가 다르면 동일 item 아님", 그 역도 마찬가지)과 정확히 일치하고,
    실측으로도 reading-only 그룹이 455개(단순 동음이의어 잡음)임을 확인했다.
- **evidence 모델**(`NormalizedCandidateMatchEvidence`, 자유형 blob 아님):
  각 행은 `evidenceCode`(20개 값 enum, 비교한 필드마다 SAME_*/DIFFERENT_*
  대칭 쌍 — 예: `SAME_ENTRY_ID`/`DIFFERENT_ENTRY_ID`,
  `SAME_PATTERN`/`DIFFERENT_PATTERN`, `SAME_MEANING_GLOSS`/
  `DIFFERENT_MEANING_GLOSS` 등), `fieldName`(기계 판독용 필드 키),
  `detail`(사람이 읽는 실제 비교 값, 예: `a=N2 b=N1`) 세 컬럼으로 구조화했다.
  실제 v2.1.1 분석 결과 전량을
  `build/reports/jlpt-max-profiling/conflict-analysis-actual.txt`에
  candidate-id 쌍 + assessment + evidence 전체로 출력해 사람이 그대로 검토
  가능함을 확인했다.
- **blocking 전략**(O(n²) 전수비교 회피, step 28): Vocabulary는
  (entryId)와 (normalizedSearchExpression+normalizedSearchReading) 두 키로,
  Grammar는 (unitId)와 (pattern) 두 키로만 그룹을 만들고 그룹 크기 ≥2인
  경우에만 그룹 내부 쌍을 비교 대상으로 삼는다. 실측 결과 실제 비교 대상
  쌍은 Vocabulary 1쌍 + Grammar 32쌍 = **총 33쌍**뿐이었다(10,238건 대비) —
  cluster/union-find 같은 별도 구조 없이 단순 pairwise 저장으로 충분함을
  숫자로 확인했다(3개 이상 겹치는 그룹은 Grammar で 6건 그룹 등 존재하지만
  최대 pair 수 C(6,2)=15로 여전히 사소한 규모).
- **comparison-only normalization**(`comparisonKey`): NFKC 유니코드 정규화 +
  trim만 수행하고 조사·기호·괄호·～ 표기·공백 내부 구조는 건드리지 않는다.
  Vocabulary는 Ticket 2가 이미 계산해 둔
  `normalizedSearchExpression`/`normalizedSearchReading`을 우선 사용하고
  (source raw 필드는 절대 mutate하지 않음), 없을 때만 raw
  expression/reading에 `comparisonKey`를 적용한다.
- **FATAL candidate 처리**(step 19): 조용히 제외하지 않는다 — blocking이
  entryId/unitId 또는 expression+reading/pattern이 실제로 존재하는
  candidate만 그룹화하므로, FATAL candidate라도 identity 힌트가 남아 있으면
  정상적으로 비교 대상에 포함된다(`p_fatalCandidateIsStillCompared` 테스트:
  EntryID는 있지만 expression/reading이 전부 null인 FATAL candidate가 같은
  EntryID의 정상 candidate와 CONFLICT로 정확히 판정됨을 확인). 비교할 만한
  identity 힌트가 전혀 없는 candidate(entryId도 expression+reading도 전부
  없음)는 어떤 블로킹 그룹에도 들어가지 않아 결과적으로 UNIQUE가 되는데,
  이는 "숨겨진 누락"이 아니라 "비교 대상이 없어 유일한 것으로 계산됨"이라는
  명시적이고 설명 가능한 결과다(실제 v2.1.1의 유일한 Vocabulary FATAL
  1건이 이 경로를 탄다).
- **provenance scope**(step 16): 비교는 항상 같은 `(candidateType,
  sourceRef)` scope 내부로 한정했다(`NormalizedCandidateConflictAnalyzer.analyze(type,
  sourceRef)`가 그 scope의 candidate만 로드) — EntryID/UnitID가
  source-native identity이기 때문이다. 현재 이 코드베이스에는 JLPT-MAX
  단일 source만 존재해 이 scoping이 오늘 당장 결과를 바꾸지는 않지만,
  향후 multi-source 통합 시 cross-source identity reconciliation은 별도
  concern으로 분리된다 — 지금은 그 framework를 만들지 않았다.
- **pair 저장 설계**(`NormalizedCandidateMatchPair`): `leftCandidate`/
  `rightCandidate`를 생성자에서 항상 ascending id로 canonical ordering하고
  (호출자의 인자 순서와 무관), self-match와 cross-candidateType pair는
  생성자에서 `IllegalArgumentException`으로 즉시 거부한다(entity 차원의
  방어 — `NormalizedCandidateMatchPairTest` 5건으로 고정). DB에도
  `(left_candidate_id, right_candidate_id)` unique 제약을 걸었다. CHECK
  제약(`left < right`)은 MySQL 배포 버전을 확정할 수 없어 이식성 문제로
  넣지 않았고, 대신 service invariant + entity 단위 테스트로 방어했다
  (Ticket이 명시적으로 허용한 대안).
- **UNIQUE 미저장 설계**(step 21): 모든 candidate에 UNIQUE row를 저장하지
  않는다 — pair 관계가 실제로 존재하는 candidate만 행을 가지며, UNIQUE는
  "그 candidateType+sourceRef scope 안에서 pair가 하나도 없는 candidate
  수"로 계산되는 파생값(`NormalizedCandidateAnalysisSummary.uniqueCount()`)이다.
  4C가 conflict/possible-duplicate 목록을 조회하려면
  `NormalizedCandidateMatchPairRepository`의
  `findByLeftCandidate_CandidateTypeAndLeftCandidate_SourceRef(AndAssessment)`로
  충분하다 — 별도 summary 테이블을 두지 않아 테이블 중복을 피했다.
- **group vs pair**(step 22): union-find 등 cluster 구조는 만들지 않았다 —
  실측 최대 그룹 크기(Grammar で 6건, pair 15개)가 pairwise 저장으로
  충분히 감당 가능한 규모임을 확인했기 때문이다(전체 실제 pair 수도 33개).
- **idempotency/rerun**(step 17/18): `analyze(type, sourceRef)`를 다시
  실행하면 그 scope의 기존 pair(+evidence, cascade)를 벌크 JPQL delete로
  전부 지우고 현재 candidate 스냅샷으로 처음부터 재계산한다 — 버전
  관리/diff 없이 delete-then-regenerate만 수행하며, 각 pair의
  `generatedAt`만 최신 재계산 시각을 나타낸다. 별도의 "analysis run"
  테이블은 두지 않았다. `n_rerunningOnUnchangedPopulationIsIdempotent`
  테스트로 pair 수/assessment/evidence가 재실행 후에도 동일함을(단, row
  id 자체는 delete-then-regenerate이므로 바뀜 — 이는 설계상 정상) 확인했고,
  실제 APKG에서도 `analyze`를 연속 2회 호출해 두 번째 결과가 첫 번째와
  완전히 동일한 `NormalizedCandidateAnalysisSummary`임을 검증했다
  (`NormalizedCandidateConflictAnalyzerRealApkgReport`).
- **candidate refresh 후 재분석**(step 17): Ticket 4A의 refresh(같은
  sourceRef+sourceNoteId 재저장)로 candidate 내용이 바뀐 뒤 재분석하면
  이전 평가에 대응하는 stale pair/evidence가 남지 않음을
  `o_candidateRefreshThenRerunLeavesNoStaleEvidence` 테스트로 확인했다
  (EXACT_DUPLICATE였던 쌍이 meaning 변경 후 재실행하면 POSSIBLE_DUPLICATE로
  갱신되고, evidence 행 수가 실제 저장된 evidence와 정확히 일치).
- **cross-type/self-match 방지**(step 14/15): analyzer는 한 번에 하나의
  `candidateType`만 로드하므로 구조적으로 cross-type pair가 생성될 수
  없고, entity 생성자가 추가로 이를 방어한다
  (`k_crossTypeCandidatesAreNeverPaired`/`l_selfMatchNeverPersists`,
  그리고 entity 단위 `crossTypeMatchIsRejected`/`selfMatchIsRejected`).
- **production isolation**(step 23/24): `ContentItem`/`Word`/`Grammar`/
  `GrammarEnrichment`/`GrammarRelation`/`GrammarComparison`/
  `ImportedSourceRecord`을 생성·수정·참조하지 않는다 —
  `GrammarRelation`/`GrammarComparison`은 이름이 비슷해 보이지만 curated
  production 도메인이므로 재사용하지 않고 이번 Ticket 전용 private analysis
  domain(`normalized_candidate_match_pairs`/`normalized_candidate_match_evidence`)을
  새로 만들었다. `q_analysisNeverChangesProductionOrImportedSourceRowCounts`
  테스트로 `content_items`/`words`/`grammars`/`GrammarEnrichment`/
  `GrammarRelation`/`GrammarComparison`/`ImportedSourceRecord` 각 count가
  분석 전후 불변임을 확인했다.
- **Migration**: `V7__add_normalized_candidate_match_analysis.sql`(H2/MySQL
  동일 의미, V1-V6 무수정)로 `normalized_candidate_match_pairs`(+ index
  on `right_candidate_id`/`assessment`)와 `normalized_candidate_match_evidence`
  2개 테이블을 추가했다. `FlywayMigrationTest`에 V7 fresh-DB/V6→V7
  upgrade(기존 content/candidate row 보존 + 신규 테이블에 pair/evidence
  insert 가능 + unique 제약 동작 확인)/MySQL-additive 테스트를 추가하고,
  기존 V1-V6 upgrade 테스트들의 `migrationsExecuted` 기대값을 전부 +1
  갱신했다(V7 추가로 pending migration 수가 하나씩 늘어났기 때문).
- **자동 merge/production mapping/human review: 전부 없음**. 이번
  Ticket은 dedup/conflict 판정 결과를 private 영역에 저장하는 것으로
  끝나며, canonical winner 선택, candidate 삭제, `Word`/`Grammar` 갱신,
  reviewer 승인/거부, Release Gate, source rights/publication 변경,
  COMPREHENSIVE parser는 전부 범위 밖이다(4C 이후 과제).
- **테스트**: `NormalizedCandidateMatchPairTest`(5건, entity invariant),
  `NormalizedCandidateConflictAnalyzerTest`(19건, synthetic A-Q 전체
  시나리오), `FlywayMigrationTest`(V7 관련 2건 신규 + 기존 5건 기대값
  보정), 신규 opt-in `NormalizedCandidateConflictProfilingReport`(actual
  raw profiling)/`NormalizedCandidateConflictAnalyzerRealApkgReport`(actual
  분석 + idempotency 검증). 전체 `./gradlew clean test` 311 tests, 0
  failures, 0 errors, 10 skipped(기존 8건 opt-in + 이번 Ticket 신규 opt-in
  2건 - profiling/RealApkgReport 둘 다 opt-in 플래그 없이 실행 시 스스로
  skip되어 이 10건에 포함됨; 독립 리뷰에서 "9건+1건" 서술의 숫자 오류를
  확인해 정정함). opt-in 2건(profiling/actual analysis)은
  실제 APKG로 별도 Gradle 실행으로 통과 확인(SHA-256 재검증 포함).
  `git diff --check` 통과, 신규 파일 trailing whitespace 없음.
- **독립 리뷰 후속 하드닝(2026-09-17)**: MAJOR 1건(evidence.detail이
  `varchar(2000)`인데 `diff()`가 원문 전체를 그대로 담아 LONGTEXT/집계
  필드에서 실제로 컬럼 한도를 넘길 수 있었음)과 MINOR 다수를 수정했다 -
  evidence 표시값을 편측 최대 900자 bounded excerpt(서로게이트 페어
  안전 절단 + 명시적 truncation marker)로 제한하되 비교/판정 자체는
  항상 원문 full value로 수행하도록 분리, `blockGrammar` null-safety,
  EntryID/UnitID 둘 다 없는 경우 `DIFFERENT_*` identity evidence row
  생략, `NormalizedCandidateMatchPair` 생성자에 cross-sourceRef guard
  추가, Vocabulary EXACT 판정에 `partOfSpeech` 보조 필드 추가(pitch
  accent/example/meaning 순서는 기존대로 제외 - 근거를 Javadoc/테스트로
  명시), Grammar EXACT 제외 필드(frontExample/rawKind/confusablePatterns)
  계약을 테스트로 고정. 실제 v2.1.1 분류 결과(Vocabulary 1
  possible/Grammar 32 possible, 나머지 0)는 이번 하드닝으로 바뀌지
  않음을 opt-in 실제 APKG 재실행으로 재확인했다. stale-pair 판정
  (`normalizedAt` vs `generatedAt`)과 U+301C/U+FF5E/U+007E dash
  codepoint 통합은 이번 범위에서 다루지 않았다 - 전자는 Ticket 4C
  read-path 설계로, 후자는 실측 결과에 근거해 필요 시 별도로 검토한다.

## 2026-09-17  JLPT-MAX Ticket 4C — Normalized Candidate Pair 사람 검토(Human Review) 워크플로

- **목표**: Ticket 4B가 저장한 private `NormalizedCandidateMatchPair`/`NormalizedCandidateMatchEvidence`
  분석 결과를 사람이 안전하게 검토하고 판단(같은 content / 다른 content / 추가 확인 필요)만
  기록하는 admin 전용 워크플로. **자동 merge, candidate 삭제, canonical winner 선택, production
  승격/생성은 전부 범위 밖**이며 이번 Ticket에서 구현하지 않았다.
- **private review domain 경계**: 새 엔티티(`NormalizedCandidatePairReview`/
  `NormalizedCandidatePairReviewHistory`)는 production 검수 도메인(`ContentItem.reviewStatus`,
  `ContentReviewHistory`, `CurationReviewHistory`, `GrammarRelation`, `GrammarComparison`)을
  전혀 재사용하지 않는다 - 이름이 비슷한 `GrammarRelation`/`GrammarComparison`은 여전히 curated
  production 도메인이라 이번에도 손대지 않았다. 다만 기존 `AdminContentReviewService`/
  `AdminContentReviewController`/`AdminContentReviewExceptionHandler`의 **패턴**(PRG,
  reviewer-from-principal, append-only history, exception→HTTP status 매핑)은 그대로 재사용했다.
- **human decision 모델**(`HumanReviewDecision`): `SAME_CONTENT`/`DISTINCT_CONTENT`/
  `NEEDS_FOLLOWUP` 3값 - Ticket 4B의 machine `NormalizedCandidateMatchAssessment`(`EXACT_DUPLICATE`/
  `POSSIBLE_DUPLICATE`/`CONFLICT`)와는 완전히 분리된 축이다. `SAME_CONTENT`로 판단해도 merge·
  candidate 삭제·canonical winner 선택·production 승격은 절대 트리거하지 않으며,
  `NormalizedCandidateMatchPair.assessment`도 human decision으로 절대 덮어쓰지 않는다
  (machine/human 두 축을 항상 동시에 보존 - `NormalizedCandidatePairReviewService` 서비스
  javadoc/테스트로 고정).
- **왜 `NormalizedCandidateMatchPair.id`를 review의 FK로 쓰지 않는가**: Ticket 4B의
  `analyze(type, sourceRef)`는 재실행마다 그 scope의 기존 pair(+evidence)를 delete하고 새로
  insert한다 - candidate/assessment가 전혀 안 변해도 pair row `id`/`generatedAt`은 매번 바뀐다
  (Ticket 4B 자체의 idempotency 테스트가 이를 허용/전제). 따라서 review를 pair id에 FK로
  묶으면 (a) 4B rerun이 FK 제약으로 막히거나 (b) rerun 후 review가 고아가 되는 문제가 생긴다.
  대신 review의 안정적인 identity는 **두 `NormalizedContentCandidate`의 id 쌍**
  (`left_candidate_id < right_candidate_id`, `NormalizedCandidateMatchPair`와 동일한 canonical
  ordering을 생성자에서 직접 강제)이며, 현재 4B pair/evidence는 read-time에
  `NormalizedCandidateMatchPairRepository.findByLeftCandidateIdAndRightCandidateId(...)`로
  join해서 조회한다 - review 테이블에는 pair 테이블로의 FK가 전혀 없다.
- **current state + append-only history**: `NormalizedCandidatePairReview`(pair identity당 1행,
  `(left_candidate_id, right_candidate_id)` unique)는 재검토 시 in-place로 갱신되고,
  `NormalizedCandidatePairReviewHistory`(append-only, update/delete 경로 없음 - 생성자+getter만)는
  매 결정마다 새 행을 추가한다. History 행은 `previousDecision`/`newDecision`/reviewer/note/
  reviewedAt/snapshot을 모두 담아 그 자체로 감사 기록이 완결되도록 했다. **동일 decision+note를
  그대로 재제출하면 no-op**(history 미추가, current 미변경) - `AdminContentReviewService`의
  "이미 승인됨" idempotent short-circuit 관행을 그대로 따른 것이며
  `d_resubmittingTheSameDecisionAndNoteIsANoOp` 테스트로 고정했다.
- **reviewer identity**: `ContentReviewHistory`/`CurationReviewHistory`와 동일하게
  `UserAccount`로의 `@ManyToOne(reviewer_id)`. 컨트롤러는 client가 보낸 어떤 reviewer 필드도
  절대 신뢰하지 않고 `CurrentUserService.currentAccount()`(= 인증된 `SecurityContextHolder`
  principal)만 서비스에 넘긴다 - request body/param에 reviewer 이름을 넣을 수 있는 지점 자체가
  없다.
- **freshness - 이번 Ticket의 핵심 설계**: 두 개의 독립된 freshness 개념을 구현했다.
  1. **pair freshness**(`leftCandidate.normalizedAt <= pair.generatedAt AND
     rightCandidate.normalizedAt <= pair.generatedAt`) - 지금 이 순간 새 decision을 제출해도
     되는지를 결정한다. STALE이면 decision 제출 자체를 거부한다(`h_staleCandidateAfterPairGeneratedRejectsNewDecision`).
  2. **review freshness**(저장된 `leftNormalizedAtSnapshot`/`rightNormalizedAtSnapshot`/
     `assessmentSnapshot`이 현재 값과 정확히 일치하는지, 그리고 현재 pair가 아예 존재하는지) -
     이미 기록된 human decision이 지금도 유효한지를 결정한다. Ticket 4B `analyze`를 candidate
     변경 없이 재실행하면(pair id/generatedAt만 바뀜) review는 계속 FRESH로 남고
     (`j_unchangedReanalysisKeepsAnExistingReviewFreshDespitePairIdChanging`), candidate가
     refresh되면(정상 재실행 없이도) 즉시 STALE이 되며(`i_candidateRefreshMakesAnExistingReviewStale`),
     candidate 변경 없이 assessment만 달라지는 합성 시나리오에서도 STALE이 된다 - 두 개념
     모두 GET 요청이 어떤 DB write도 유발하지 않는 순수 계산값이며, 재분석은 항상 명시적
     `POST .../reanalyze` 액션(`NormalizedCandidateConflictAnalyzer.analyze`를 그대로 호출)으로만
     수행된다.
  3. **pair가 사라지는 경우**: candidate refresh 후 재분석으로 더 이상 어떤 관계도 없다고
     판정되면 4B pair row 자체가 사라진다. 이 경우도 review/history는 삭제하지 않고, 계산값
     `ReviewFreshness.ANALYSIS_NO_LONGER_PRESENT`로 명확히 구분해 표시한다 - 별도의 archive
     테이블/soft-delete 플래그를 새로 만들지 않았다(`l_pairDisappearingPreservesReviewAndHistoryWithoutCrashing`).
- **review action 낙관적 동시성**: 재검토 시 폼이 `expectedPairGeneratedAt`/`expectedAssessment`
  (4B 분석이 화면을 그린 이후 바뀌었는지)와, 기존 review가 있다면 `expectedReviewVersion`
  (다른 admin이 먼저 재검토했는지)을 hidden field로 실어 보낸다. 서비스는 제출 시 이 값들을
  현재 DB 상태와 다시 비교해 하나라도 어긋나면 `NormalizedCandidatePairReviewConflictException`
  (409)으로 거부한다 - 조용히 덮어쓰지 않는다. `NormalizedCandidatePairReview.version`에
  `@Version`(이 코드베이스 최초의 optimistic-locking 사용)을 추가해 두 admin의 동시 재검토도
  방어했다(`n_concurrentReReviewWithAStaleVersionIsRejected`).
- **query 설계 / N+1 회피**: admin list는 33건(현재 실측) 규모이지만 향후 커질 수 있어
  `NormalizedCandidateMatchPairRepository`에 후보 타입별 `JOIN FETCH`
  (`findVocabularyPairsForReview`/`findGrammarPairsForReview` - candidate와 그 vocabulary/grammar
  detail까지 한 번에) 쿼리를 추가하고, review 쪽도 `findForReviewList`로 한 번에 읽어 메모리에서
  `(leftId,rightId)` 키로 join한다 - 목록 렌더링이 pair 수만큼 추가 쿼리를 내지 않는다. 필터(사람
  판단/freshness는 저장된 컬럼이 아니라 계산값이므로) 및 페이지네이션은 이 in-memory 리스트
  위에서 수행했다 - 현재 규모에서 별도 dynamic query/Specification 엔진을 새로 만드는 것은
  overengineering이라 판단했다.
- **admin UI**: `admin/normalized-candidate-review-list.html`/`-detail.html`을 기존
  `admin/content-list.html`/`content-detail.html`과 동일한 스타일(`page-shell admin-shell`,
  `status-pill`, `admin-table`, `admin-facts`, `review-history`, PRG 플래시 메시지)로 새로
  추가했다. Detail 화면은 evidence(Ticket 4B, truncated 가능)뿐 아니라 candidate의 **전체
  persisted 필드**(entryId/expression/reading/meanings/examples 또는 unitId/pattern/
  meaningGloss/nuance/connection/frontExample/confusablePatterns, warnings, qualityState 포함)를
  나란히 보여준다 - evidence.detail만 보고 판단하지 않도록. FATAL candidate는 강하게 표시하되
  자동 제외/숨김은 하지 않는다. Ticket 4B pair 존재 여부와 무관하게 review/history는 항상 조회
  가능하다(pair 사라진 경우도 크래시 없이 렌더링).
- **web-only, API 없음**: `AdminContentReviewController`+`AdminContentReviewApiController`
  쌍과 달리 이번 Ticket은 `AdminNormalizedCandidateReviewController`(web) 하나만 추가했다 -
  이 admin UI를 소비하는 기존 JS 클라이언트가 없고, 서버 렌더 폼만으로 모든 액션(목록/상세/
  판단 제출/재분석)이 충분하며, 짝을 맞추기 위해서만 API 컨트롤러를 만드는 것은 이번 Ticket이
  명시적으로 경계한 overengineering이라 판단했다. 두 경로 모두 `/admin/**` 아래에 있어
  기존 `SecurityConfig`의 `hasRole("ADMIN")` 매처가 그대로 적용된다 - 보안 설정 변경은 전혀
  없었다.
- **UNREVIEWED 비저장**: review row가 없는 pair는 계산상 `UNREVIEWED`로 취급될 뿐 빈 row를
  미리 만들어 두지 않는다 - 실측 33건이든 향후 수천 건이든 review 테이블에는 실제로 검토된
  pair만 존재한다.
- **Migration**: `V8__add_normalized_candidate_pair_review.sql`(H2/MySQL 동일 의미, V1-V7
  무수정)로 `normalized_candidate_pair_reviews`(+ `(left_candidate_id, right_candidate_id)`
  unique)와 `normalized_candidate_pair_review_history`(+ pair 조회용 index) 2개 테이블을
  추가했다. 두 테이블 모두 `normalized_content_candidates(id)`에 직접 FK를 걸었을 뿐
  `normalized_candidate_match_pairs`로의 FK는 전혀 없다. `FlywayMigrationTest`에 V8 fresh-DB
  기대값(8개 마이그레이션, 신규 테이블 2개) 갱신, V7→V8 upgrade 테스트(기존 candidate/pair/
  evidence 보존 + 신규 테이블 insert 가능 + unique 제약 동작 확인) 및 MySQL-additive 테스트를
  추가했고, 기존 V1-V7 upgrade 테스트들의 `migrationsExecuted` 기대값을 전부 +1 갱신했다(Ticket
  4B가 V7 추가 때 했던 것과 동일한 패턴).
- **production isolation**: `content_items`/`GrammarEnrichment`/`GrammarRelation`/
  `GrammarComparison`/`ImportedSourceRecord` row 수가 review 판단 저장/재검토/재분석 전후로
  불변임을 `m_reviewOperationsNeverChangeProductionOrImportedSourceRowCounts` 테스트로 확인했다.
  source rights/publication/Release Gate 관련 엔티티는 이번 Ticket에서 전혀 참조하지 않았다.
- **테스트**: `NormalizedCandidatePairReviewTest`(5건, entity invariant - self/cross-type/
  cross-sourceRef 거부 + canonical ordering이 candidate와 snapshot 모두에 적용됨),
  `NormalizedCandidatePairReviewServiceTest`(12건, A-N 시나리오 - 최초 결정/history append/
  재검토/no-op 재제출/존재하지 않는 pair 거부/cross-sourceRef 거부/stale pair 거부/candidate
  refresh 후 stale/unchanged reanalysis 후에도 fresh 유지/pair 소멸 후 history 보존/동시
  재검토 optimistic lock 거부/production isolation), `AdminNormalizedCandidateReviewControllerTest`
  (6건, ADMIN-only 웹 라우트 + CSRF + PRG + 404/409 처리), `FlywayMigrationTest`(V8 관련 2건
  신규 + 기존 7건 기대값 보정). 전체 `./gradlew clean test` 349 tests, 0 failures, 0 errors,
  10 skipped(기존 opt-in 그대로 - 이번 Ticket은 opt-in 테스트를 추가하지 않았다). `git diff
  --check` 통과.
- **이번 Ticket에서 하지 않은 것(명시적 비범위)**: automatic merge, candidate 삭제, canonical
  winner 선택, production `Word`/`Grammar`/`ContentItem` 생성, `ImportedSourceRecord` 연결,
  production 승격, source rights 승인, publication, Release Gate 연동, batch release,
  production `reviewStatus` 변경, COMPREHENSIVE parser 변경, cluster/union-find review 엔진,
  UNIQUE candidate 10k건 개별 review UI. 전부 4C 이후 과제로 남는다.

## 2026-09-17  JLPT-MAX Ticket 4D — Normalized Candidate Promotion Readiness / Dry-run Planner

- **왜 실제 promotion이 아니라 dry-run/readiness인가**: 이번 Ticket은 "이 candidate를 production
  draft로 승격할 수 있는가? 안 된다면 정확히 무엇이 막고 있는가?"를 deterministic하게 계산하는
  **read-only planner**만 추가한다. `ContentItem`/`Word`/`Grammar`/`Meaning`/`Example` 생성,
  `ImportedSourceRecord.linkContentItem`, candidate merge/삭제, canonical winner 선택, production
  전역 identity/slug 확정, source rights 변경은 전부 이번 Ticket의 범위 밖이며 코드 어디에도
  없다 - Ticket 4A~4C가 "직접 production에 쓰지 않고 private staging/dedup/review 계층을 먼저
  만든" 것과 같은 이유로, 승격 여부 판단 로직도 실제 승격 실행보다 먼저, 별도로 검증 가능해야
  한다고 판단했다.
- **private→production field mismatch**: `NormalizedVocabularyCandidateDetail`은 일부 필드를
  production `Word`(expression/reading/partOfSpeech 120자, pitchAccent 500자)보다 넉넉하게
  (500자) 보존한다 - 손실 없이 원본을 유지하기 위한 의도적 설계(Ticket 4A)이므로, 이번 Ticket은
  candidate 필드 값을 **자르지 않고** production 한도를 초과하면 명시적 blocker
  (`VOCAB_EXPRESSION_TOO_LONG` 등)로 막는다. Vocabulary 매핑은 실제로 해소 가능했다 -
  expression/reading/partOfSpeech는 `ApkgVocabularyImporter`(현재 유일한 production `Word`
  writer)가 쓰는 것과 동일한 `AnkiFieldTextNormalizer.text(...)` 결과이고, pitchAccent는 그
  importer의 유일한 `Word.pitchAccent` 직렬화 포맷(`"terminal="+t+";mora="+m`)을 그대로
  재사용했으며, meaning은 그 importer가 쓰는 유일한 언어 태그("ko")와 동일하고, Vocabulary
  example은 `VocabularyNormalizationParser`가 production과 **동일한** `ExampleHtmlParser`로
  파싱한 결과라 무손실이다.
- **Grammar explanation mapping unresolved policy**: production `Grammar.explanation`은
  NOT NULL(2000자)이지만, `GrammarNormalizationResult`(Ticket 3B-1)에는 이에 직접 대응하는
  generic explanation 필드가 없다 - `meaningGloss`(짧은 한국어 gloss)와
  `frontExample.translation`(예문 번역)은 서로 다른 값이고, 어느 것을(혹은 nuance와 결합해)
  explanation으로 쓸지 이 코드베이스에 ratified contract가 전혀 없다(`GrammarNormalizationResult`
  자체 class javadoc이 "이것은 promotion-time decision, 여기서 결정하지 않는다"고 명시).
  임의로 결정하지 않고 **모든 Grammar candidate**에 무조건 `GRAMMAR_MAPPING_POLICY_UNRESOLVED`
  blocker를 부여했다 - 실측 결과 Grammar 1,078건 전부가 이 코드로 막힌다(아래 프로파일링).
  `frontExample`/`confusablePatterns`을 production `Example`/`GrammarRelation`/
  `GrammarComparison`으로 자동 매핑하는 것도 동일한 이유로 하지 않았고 같은 blocker 코드 아래
  묶었다. 유일하게 해소된 Grammar 필드는 `pattern`(→`Grammar.pattern`, 200자 한도)과
  `connectionForm`(→`Grammar.connection`, "접속" 카드가 명확히 label-identified되어 있어
  ambiguity 없음, 500자 한도)뿐이다.
- **global production identity/slug unresolved**: `sourceNoteId`/`EntryID`/`UnitID`/정규화된
  expression+reading/pattern 중 어느 것도 production `ContentItem.slug`(unique, 120자) 전역
  identity로 자동 확정하지 않았다. `ApkgVocabularyImporter`(레거시, private candidate 파이프라인
  전체를 우회하는 별도 경로)가 `"jlpt-max-"+entryId`/`"jlpt-max-grammar-"+noteId` 식 slug를
  이미 쓰고 있지만, 이는 Ticket 4A~4D가 만든 dedup/review 계층을 거치지 않는 구식 관행이라
  ratified policy로 채택하지 않았다 - 대신 **모든 candidate**에 무조건
  `PRODUCTION_IDENTITY_POLICY_UNRESOLVED`를 부여했다. 그 결과 실측 데이터에서
  `READY_FOR_DRAFT_PROMOTION`은 정확히 0건이다 - 이는 버그가 아니라 이번 Ticket이 의도적으로
  아직 내리지 않은 결정을 정직하게 드러낸 것이다.
- **pair review semantics / SAME_CONTENT가 canonical winner를 의미하지 않는 이유**: Ticket 4B
  pair(`NormalizedCandidateMatchPair`)와 Ticket 4C human review(`NormalizedCandidatePairReview`)
  는 그대로 재사용(read-only)했을 뿐 어느 쪽도 변경하지 않았다. candidate별로 참여 중인 **현재**
  pair를 전부 확인해, pair 자체가 stale(`PAIR_ANALYSIS_STALE`), review 없음(`PAIR_UNREVIEWED`),
  review가 stale(`PAIR_REVIEW_STALE`), `NEEDS_FOLLOWUP`, `SAME_CONTENT`(→
  `SAME_CONTENT_CANONICAL_SELECTION_REQUIRED`) 중 하나라도 해당하면 pair 축을 BLOCKED로
  표시한다. `SAME_CONTENT`는 "두 candidate가 같은 content"라는 사람의 판단만 기록할 뿐 어느
  쪽이 canonical인지, merge 전략이 무엇인지는 4C가 전혀 정하지 않았으므로 이번 Ticket도 정하지
  않았다 - 그래서 `SAME_CONTENT` pair에 속한 candidate는 양쪽 모두 승격 준비 완료로 만들지
  않는다. pair가 없는 candidate는 그 자체로 pair 축 통과(누락이 아니라 "현재 dedup/conflict
  관계 없음"이라는 의미)이며, 과거 pair가 재분석으로 사라지고 review/history만 남은 경우도
  **현재** pair가 기준이므로 과거 관계로 blocker를 만들지 않는다.
- **quality blocker policy**: `NormalizedCandidateQualityState.FATAL`/`REVIEW_REQUIRED`는
  무조건 BLOCK(`NORMALIZATION_FATAL`/`NORMALIZATION_REVIEW_REQUIRED`), `CLEAN`/`INFORMATIONAL`은
  quality 축 자체로는 통과다 - Ticket 4C human pair review는 관계 review이지 normalization
  warning 자체를 승인하는 워크플로가 아니므로, pair review를 했다고 `REVIEW_REQUIRED`/`FATAL`이
  사라지지 않는다(두 축은 완전히 독립).
- **rights와 draft mapping readiness 분리**: `mappingStatus`(candidate 필드가 production 스키마에
  기술적으로 맞는가)와 `sourceRightsStatus`(향후 공개 가능한 rights 상태인가)를 별도 필드로
  분리해 DTO/화면에 노출한다 - `mappingReady=true`이면서 `rights=BLOCKED`인 조합이 실제로
  가능하고 의미가 다르기 때문이다. `ContentSourceRightsService.releaseEligibility(sourceRef)`를
  그대로 재사용했을 뿐(read-only) 어떤 `ContentSource.rightsStatus`도 변경하지 않았고, JLPT-MAX
  composite source를 자동 `ALLOWED`로 만들지도 않았다(실측 시나리오에서는 `ContentSource` row
  자체가 없어 전부 `SOURCE_NOT_REGISTERED`).
- **existing production provenance 감지**: `ImportedSourceRecord`의 `(source_ref, note_type,
  source_note_id)` unique identity로 이미 production `ContentItem`에 연결된 candidate를
  read-only로 감지한다(`ALREADY_PROMOTED`). `candidateType → noteType` 대응은 추측이 아니라
  이 코드베이스가 이미 두 곳에서 독립적으로 인코딩한 사실이다: `PrivateApkgExtractor.category()`
  가 정확히 `"JLPT MAX덱 어휘"`/`"JLPT MAX덱 문법"` 문자열을 VOCABULARY/GRAMMAR로 인식하고,
  `ImportedSourceRecord`의 유일한 writer인 `ApkgVocabularyImporter`가 그 두 문자열을 그대로
  `noteType`으로 쓴다. `getContentItem() != null`까지 확인해 "record가 있다"가 아니라 "실제로
  ContentItem에 연결됐다"만 `ALREADY_PROMOTED`로 센다. Write는 전혀 없다.
- **zero-write guarantee**: `NormalizedCandidatePromotionReadinessService`의 모든 public
  method는 `@Transactional(readOnly = true)`이고, repository `save`/`delete` 호출이 단 한 줄도
  없다(entity setter 호출도 없음 - 전부 getter만 사용). `readinessComputationNeverWritesAnyRowAnywhere`
  테스트로 candidate/pair/review/history/contentItem/word/grammar/example/
  importedSourceRecord/contentSource/level/grammarEnrichment/grammarRelation/grammarComparison
  13개 테이블 row count가 `list`/`summary`/`detail` 반복 호출 전후 불변임을 확인했다.
- **batch/N+1 회피**: candidate 최대 ~10k건 규모(실측 Vocabulary 9,160 + Grammar 1,078)를
  대상으로, candidate당 반복 쿼리를 절대 내지 않도록 (1) candidate + vocabularyDetail/
  grammarDetail을 한 번에 fetch-join하는 새 쿼리
  (`findByCandidateTypeAndSourceRefWithDetailForReadiness`), (2) Vocabulary meanings/examples는
  candidate id `IN` 절 두 번의 별도 batch 쿼리로 로드(List 연관을 두 개 이상 fetch-join하면
  Hibernate `MultipleBagFetchException` 위험이 있어 분리), (3) 현재 Ticket 4B pair는 scope
  전체를 한 번에 읽어(`findByCandidateTypeAndOptionalSourceRef`) candidate id별
  `Map<Long,List<Pair>>`로 메모리에서 구성, (4) Ticket 4C review는 기존
  `findForReviewList`로 scope 전체를 한 번에 읽어 `(leftId,rightId)` 키 맵으로 구성, (5) JLPT
  `Level`은 전체를 한 번만 읽어 `Set<String>`으로, (6) source rights/existing-provenance는
  scope 내 **distinct sourceRef별로만**(보통 1개) 조회한다. 실측 결과 Vocabulary 9,160건 +
  Grammar 1,078건 전체 스캔이 각각 수 초 내로 끝났다(opt-in report 기준).
- **admin UI**: `/admin/normalized-candidates/promotion-readiness`(list, 유형/sourceRef/전체
  상태/blocker code/quality 필터 + scope 요약 패널) +
  `/admin/normalized-candidates/promotion-readiness/{candidateType}/{candidateId}`(detail, 축별
  상태 + blocker 전체 목록 + candidate 전체 필드 + mapping preview + 현재 pair 목록)를
  기존 `admin/normalized-candidate-review-*` 스타일 그대로 추가했다. **POST 매핑이 전혀 없다**
  - 100% 조회 전용이며, `/admin/**`이 이미 `ROLE_ADMIN`으로 제한돼 있어 `SecurityConfig` 변경도
    없었다(`AdminNormalizedCandidatePromotionReadinessControllerTest`로 확인).
- **actual v2.1.1 profiling** (opt-in, `NormalizedCandidatePromotionReadinessRealApkgReport`,
  SHA-256 `9d8be3ff6b23e11ef890a146dffec7ec4649de4bcbd491be439a11b991fd154d` 검증 후 실행,
  scope별 fresh in-memory H2):
  - **Vocabulary (9,160건)**: READY_FOR_DRAFT_PROMOTION 0 · BLOCKED 9,160. quality CLEAN 9,159/
    FATAL 1(entryId/expression/reading/meaning이 모두 없는 손상 note 1건). pair 축 BLOCKED 2건
    (Ticket 4B 실측 POSSIBLE_DUPLICATE pair 1개의 양쪽, 아직 미검토=`PAIR_UNREVIEWED`) /
    RESOLVED 9,158건. mapping 축 BLOCKED 8,381 / READY 779 - 전부 `JLPT_LEVEL_UNMAPPABLE`이
    원인이며, 이는 필드 길이 문제가 아니라 이번 profiling을 "sample" 프로파일의 fresh DB(오직
    `Level(JLPT,N5)` 1행만 시드됨)에서 실행했기 때문 - 실제 운영 DB처럼 N1~N5 `Level` row가
    전부 존재하면 이 축의 결과는 달라진다(별도 명시). 필드 길이 blocker(expression/reading/
    partOfSpeech/pitchAccent/meaning/example TOO_LONG)는 실측 0건 - v2.1.1 실제 데이터는 현재
    production 컬럼 한도를 초과하지 않는다. source rights는 전부 `SOURCE_NOT_REGISTERED`(의도적
    미등록). `PRODUCTION_IDENTITY_POLICY_UNRESOLVED` 9,160건(전부).
  - **Grammar (1,078건)**: READY_FOR_DRAFT_PROMOTION 0 · BLOCKED 1,078(전부).
    quality CLEAN 1,078(전부 - Ticket 3B-1 scope가 이미 IsBasic-only로 필터링됨). pair 축
    BLOCKED 26 / RESOLVED 1,052(Ticket 4B 실측 POSSIBLE_DUPLICATE pair 32개에 관련된
    candidate 중 아직 미검토인 26건). mapping 축은 1,078건 전부 BLOCKED -
    `GRAMMAR_MAPPING_POLICY_UNRESOLVED`(전부, 의도된 결과) + `JLPT_LEVEL_UNMAPPABLE` 979건
    (Vocabulary와 동일한 이유 - fresh sample DB에 N5 Level만 존재). pattern/connection
    길이 초과(TOO_LONG)는 실측 0건. `PRODUCTION_IDENTITY_POLICY_UNRESOLVED` 1,078건(전부).
  - 두 도메인 모두 `summary()`를 동일 스냅샷에서 두 번 호출해 완전히 동일한 결과(불변 필드
    단위 `equals`)를 확인했다(deterministic/idempotent 요구사항).
  - **"actual APKG profiling" ≠ "운영 DB 상태 검증"**: 실제인 것은 v2.1.1 APKG 파일의
    bytes/SHA-256과 그 안의 실제 note data뿐이다. DB는 매 실행마다 새로 만들어지는 isolated
    in-memory H2(fresh)이고, sourceRef("ticket4d-promotion-readiness")도 profiling 전용
    synthetic scope다 - 운영에서 쓰는 실제 sourceRef가 아니다. 운영 DB의 실제 ContentSource
    rights 상태, 운영 DB의 실제 N1-N5 `Level` row 완결성은 이번 프로파일링이 검증한 적이 없다.
    Ticket 4E 전에는 운영과 동일한 상태 기준의 재프로파일링이 필요하다(아래 "Ticket 4E로
    넘기는 결정사항" (7)).
- **source rights actual 상태**: 실측 scope("ticket4d-promotion-readiness")에는 `ContentSource`
  row를 전혀 등록하지 않았다 - 실제 canonical `JLPT-MAX-Deck-2.1.1.apkg` sourceRef의
  `ContentSourceRightsStatus`를 이번 Ticket이 조회/변경한 적은 없다(이 코드베이스에 아직
  등록되지 않았을 가능성이 높다는 것만 확인). 등록/전환은 여전히 `ContentSourceRightsService`의
  기존 admin 절차를 통해서만 이뤄져야 한다.
- **query/repository 추가**: `NormalizedContentCandidateRepository.
  findByCandidateTypeAndSourceRefWithDetailForReadiness`, `NormalizedCandidateMatchPairRepository.
  findByCandidateTypeAndOptionalSourceRef`/`findByEitherCandidateIdWithEvidence`,
  `ImportedSourceRecordRepository.findBySourceRefAndNoteTypeAndSourceNoteIdIn` - 전부 추가만
  했을 뿐 기존 쿼리 메서드는 하나도 수정하지 않았다. **Migration 없음** - 이번 Ticket은 어떤
  스키마도 추가/변경하지 않는다(V8까지 그대로).
- **테스트**: `NormalizedCandidatePromotionReadinessServiceTest`(quality A-D, pair E-L(+pair
  자체 stale 케이스 별도 1건), Vocabulary mapping M-S, Grammar mapping T-W, production identity
  X-Z, source rights 4건, already-promoted 2건, zero-write 1건, determinism 2건 = 총 27건),
  `AdminNormalizedCandidatePromotionReadinessControllerTest`(ADMIN-only list/detail, 404,
  잘못된 enum 필터 값 400 = 4건), `NormalizedCandidatePromotionReadinessRealApkgReport`(opt-in,
  SHA-256 게이팅, 실제 deck 대상 summary 산출 + idempotency + 구조적 회귀 가드). 전체
  `./gradlew clean test` 0 failures, 0 errors. `git diff --check` 통과(trailing whitespace 없음,
  기존 LF/CRLF 경고만). (정정: 이 항목에는 당시 "405 tests"로 기록했으나, JUnit XML 직접 집계로
  재확인한 실제 aggregate는 397 tests(skipped 11, 72 test class)다. 405는 위 lettered sub-case
  수기 tally가 자체 합계(35건)와도 맞지 않는 기존 문서 오류였으며, test 삭제·회귀가 원인이
  아니다.)
- **Ticket 4E로 넘기는 결정사항**: (1) Grammar `explanation` 매핑 정책(meaningGloss/nuance/
  frontExample 중 무엇을, 어떻게 결합할지) 확정, (2) `frontExample`/`confusablePatterns`을
  production `Example`/`GrammarRelation`/`GrammarComparison`으로 옮길지/어떻게 옮길지 결정,
  (3) production 전역 identity/slug 정책 확정(EntryID/UnitID/sourceNoteId 중 무엇을 기반으로
  할지, 충돌 시 처리), (4) `SAME_CONTENT` pair의 canonical winner 선택 및 merge 전략, (5)
  실제 draft 생성(ContentItem/Word/Grammar/Meaning/Example insert, `ImportedSourceRecord.
  linkContentItem`) 및 그 원자성/재시도 정책, (6) unpublished/PENDING draft에 한해 rights
  미결정 상태에서도 draft 생성을 허용할지 여부, (7) 운영 DB 기준(모든 N1-N5 `Level` row가 이미
  존재하는 상태) 재프로파일링.
- **이번 Ticket에서 하지 않은 것(명시적 비범위)**: `ContentItem`/`Word`/`Grammar`/`Meaning`/
  `Example` insert/update, `ImportedSourceRecord.linkContentItem`, automatic merge, candidate
  삭제, canonical winner 선택, slug assignment 정책 확정, source rights 변경, publication,
  Release Gate 변경, production `reviewStatus` 변경, `GrammarEnrichment`/`GrammarRelation`/
  `GrammarComparison` 자동 생성, COMPREHENSIVE parser, V1-V8 migration 수정. 전부 4D 이후
  과제로 남는다.
