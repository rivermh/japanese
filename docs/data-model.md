# Japanese 콘텐츠 데이터 모델

## 설계 원칙

콘텐츠의 공통 검색·분류 단위는 `ContentItem`이다. `ContentItem`은 학습 콘텐츠의 식별자, 공개 상태, 출처를 가지고 `Word` 또는 `Grammar`와 일대일로 연결된다. 따라서 화면이나 API는 단어와 문법을 별도 모델로 다시 조합하지 않고 공통 조회 서비스를 사용할 수 있다.

`JLPT`는 별도 테이블 묶음이나 콘텐츠 상속 구조가 아니다. `Level`의 `system`과 `code`에 `JLPT`와 `N5`부터 `N1`을 저장하고 `ContentItem`과 다대다로 연결한다. 나중에 다른 평가 체계나 과정이 필요하면 같은 `Level`에 다른 `system`을 추가한다.

## 관계

```text
ContentItem 1 ── 1 Word ── N Meaning
     │             │
     │             └────── N Example
     │
     ├── 1 Grammar ─────── N Example
     ├── N Category
     └── N Level(system, code)
```

`Example`은 `ContentItem`에 연결되므로 단어와 문법 모두 예문을 가질 수 있다. 단어 예문은 필요할 때 `Meaning`에도 연결하여 다의어별 대표 예문을 표현한다. 번역과 읽기는 예문 자체의 값으로 두어 언어별 데이터가 늘어날 때 별도 확장을 할 수 있게 했다.

`Category`는 `jlpt`, `it`, `hotel`, `business`, `honorifics` 같은 주제·사용 영역을 표현한다. `Level`은 숙련도나 시험 체계를 표현하므로 Category와 역할을 섞지 않는다. `Word.partOfSpeech`는 초기에는 원자료의 표현을 보존하는 문자열로 두고, 향후 표준 품사 사전이 필요할 때 별도 코드 테이블로 확장한다.

문제 카드와 참조표처럼 아직 전용 학습 모델이 확정되지 않은 원자료는 `ImportedSourceRecord`에 source ref, note type, 원본 note ID, 필드 이름과 payload를 보존한다. 이 staging 레코드는 현재 콘텐츠 검색에 노출하지 않고, 추후 문제 풀이·참조표 기능을 추가할 때 안정적인 변환 원본으로 사용한다.

## 현재 구현 범위

현재는 읽기 검색과 웹 화면, 콘텐츠 상세 화면, 같은 서비스 계층을 호출하는 `/api/v1/contents` 조회 API, 단어·뜻·예문·문법·분류·레벨의 JPA 모델을 제공한다. 어휘·문법 전체 import는 `import-sample` 프로필에서만 실행되며 검토 상태로 저장된다. 학습 기록은 `LearnerProfile`과 `StudyRecord`로 분리하고, 캐릭터 레벨과 EXP를 학습 서비스가 계산하므로 이후 인증·복습 알고리즘·캐릭터 성장 규칙을 연결할 수 있다. 출처는 `ContentSource`로 별도 관리한다.
