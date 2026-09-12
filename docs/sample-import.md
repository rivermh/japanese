# APKG 샘플 importer

전체 덱을 일반 애플리케이션 시작 시 자동으로 읽지 않도록 importer는 `import-sample` 프로필에서만 활성화된다. `japanese.import.limit=0`이면 활성 어휘 전체와 문법 전체를 가져오며, 가져온 콘텐츠는 검토 전까지 `published=false`로 저장된다.

APKG 파일이 로컬에 있을 때만 아래처럼 경로를 지정해 실행한다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=import-sample --japanese.import.apkg-path=C:\path\to\JLPT-MAX-Deck-2.1.1.apkg --japanese.import.limit=0"
```

APKG 경로가 비어 있거나 파일이 없으면 애플리케이션은 importer를 건너뛰고 기존 DB 콘텐츠만 사용한다.

현재 importer는 최신 `collection.anki21`에서 `JLPT MAX덱 어휘`와 `JLPT MAX덱 문법` 노트 유형을 학습 콘텐츠로 변환한다. 어휘는 `EntryID`, `Word`, `Reading`, `Meaning`, `PartOfSpeech`, `PitchAccent`, `ExamplesRendered`, `JLPT` 또는 `WordJLPT`를 프로젝트 모델로 변환하고, 문법은 `Level`, `UnitID`, `FrontHTML`, `BackHTML`, `Kind`를 `Grammar`와 `Example`으로 변환한다. `JLPT MAX덱 어휘문제`와 `JLPT MAX덱 참조표`는 `ImportedSourceRecord`에 전체 필드 payload를 보존한다. 폐기 태그가 있는 어휘는 건너뛴다.

import-sample 프로필로 실행하면 공개 화면과 분리된 `/review`에서 `published=false`로 저장된 검토 대기 콘텐츠를 확인할 수 있다. `/review?status=PENDING` 또는 `/review?status=REJECTED`로 상태를 좁혀 볼 수 있다. 검토가 끝난 항목은 화면의 `검토 완료 · 공개` 버튼으로 공개 상태로 전환하고, 문제가 있는 항목은 반려 사유와 함께 반려할 수 있다.

승인·반려·재검토 전환은 `content_review_history`에 기록된다. 상세 화면의 `재검토 상태로` 버튼은 반려 콘텐츠를 다시 `PENDING`으로 돌리고, 기존 이력은 유지한다.

`ExamplesRendered`와 문법 뒷면 HTML은 전용 파서로 평문 문장·읽기·번역·의미를 추출한다. 오디오 파일은 복사하지 않고 안전한 파일명 참조만 보존한다. importer는 APKG를 배포 산출물로 만들지 않으며, 동일한 EntryID 또는 원본 note ID로 재실행해도 중복 레코드를 만들지 않는다.

공개 콘텐츠 조회는 `/api/v1/contents/page?page=0&size=20`에서 페이지 정보를 포함해 사용할 수 있고, 웹·Android 클라이언트가 필터 UI를 구성할 수 있도록 `/api/v1/contents/filters`에서 유형·레벨·카테고리 선택지를 제공한다. 기존 `/api/v1/contents` 목록 API도 유지한다.

기본 학습 화면은 `/study`이며, 공개 콘텐츠를 학습 카드로 제공한다. `POST /api/v1/study/cards/{slug}/answer?result=CORRECT` 또는 `INCORRECT`로 학습 결과를 기록하고, `/api/v1/study/overview`에서 학습 횟수·정답 수·캐릭터 레벨·EXP를 확인한다. 기본 학습자 키는 `japanese.learning.default-learner-key` 설정으로 교체할 수 있다.

문제 카드 학습은 `/quiz`에서 제공한다. `/api/v1/quiz/questions?level=N5&page=0&size=10`으로 목록을 조회하고 `/api/v1/quiz/questions/{id}`에서 상세 문제를 가져온다. 선택지 답변은 `POST /api/v1/quiz/questions/{id}/answer`로 확인하며, `learnerKey`를 전달하면 해당 학습자의 `QuizAttempt`와 EXP에 반영된다. 원자료의 정답 문장은 루비를 제거한 뒤 빈칸 전후를 비교해 정답 선택지를 복원한다.
