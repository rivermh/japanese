# Japanese UI 프로토타입

실제 Spring Boot 애플리케이션과 분리된 정적 디자인 검토용 프로토타입입니다. API와 DB에는 연결하지 않으며, 입력과 버튼은 브라우저 안의 시각적 상태만 바꿉니다.

## 화면

- `index.html`: Desktop Home
- `today.html`: Desktop Today Study
- `dictionary.html`: Desktop Dictionary / Search
- `mobile/index.html`: Mobile Home
- `mobile/today.html`: Mobile Today Study
- `mobile/dictionary.html`: Mobile Dictionary / Search

확정된 Home / Today / Dictionary의 구조는 유지하고 다음 화면을 같은 반응형 디자인 시스템으로 확장했습니다.

- `word-detail.html`: 단어 상세
- `grammar-detail.html`: 문법 상세
- `review.html`: 복습 허브
- `weaknesses.html`, `weakness-review.html`: 약점노트와 집중 재학습
- `progress.html`: JLPT 진도
- `weekly-report.html`: 주간 학습 리포트
- `my-learning.html`: 내 학습 허브
- `saved.html`: 북마크 / 학습 큐 / 컬렉션
- `onboarding.html`: 온보딩과 학습 플랜 설정

확장 화면은 한 HTML이 데스크톱과 모바일 레이아웃에 반응합니다. 모바일에서는 공통 하단 탐색을 사용하고, 데스크톱에서는 기존 side rail 정보 구조를 공유합니다.

## 실행

```powershell
cd design-prototype
python -m http.server 4173
```

브라우저에서 `http://localhost:4173`을 엽니다. 모바일 전용 화면은 `/mobile/`에서 확인할 수 있습니다.

## 데모 인터랙션

- Home: 작은 상태 버튼으로 Haru의 `idle / study / happy / goal complete / growth` 자리와 안내 문구를 확인할 수 있습니다.
- Today: 답변 버튼을 누르면 단어 복습 → 새 단어 → 새 문법 → 완료 상태로 이동합니다.
- Dictionary: 검색어와 콘텐츠 유형으로 현재 HTML에 포함된 샘플 결과만 필터링합니다.
- Mobile Dictionary: 필터 버튼으로 모바일 bottom sheet를 열고 닫을 수 있습니다.
- Saved: 북마크 / 학습 큐 / 컬렉션 탭을 전환할 수 있습니다.
- Onboarding: 4단계 선택 흐름을 앞뒤로 이동하고 최종 학습 시작 화면으로 연결합니다.

샘플 수치와 문장은 정보 구조와 밀도를 검토하기 위한 표시 데이터이며 저장되지 않습니다. 가짜 API 또는 백엔드 동작은 포함하지 않습니다.

## 자산

`assets/haru-young.png`는 기존 프로젝트 자산을 복사한 임시 이미지입니다. 원본은 수정하지 않았습니다. 캐릭터 컨테이너는 이후 프레임 이미지, sprite, animated WebP 또는 상태 기반 렌더러로 교체할 수 있도록 콘텐츠와 분리했습니다.

## 검수 이미지

`screenshots/`에는 데스크톱 1440px과 모바일 390px 기준으로 확인한 화면 캡처가 있습니다.

표시 데이터는 실제 도메인 규칙을 반영합니다. 북마크·학습 큐·컬렉션은 서로 독립적이며, RETRAIN은 SRS와 Streak에 반영되지만 EXP와 정규 목표를 늘리지 않습니다. 진도는 공개 콘텐츠와 현재 LearningProgress 상태를 기준으로 표현하고, 주간 리포트는 Quiz와 문법 확인을 정규 학습 집계에서 분리합니다. 문법 상세에는 승인된 curated 정보가 없을 때 빈 섹션을 만들지 않습니다.

## 2차 refinement

- Home: 장식용 창문·선반과 말풍선을 제거하고, Haru와 오늘 학습이 하나의 책상 면을 공유하도록 변경했습니다.
- Today: Haru를 원형 프로필로 표시하지 않고 콘텐츠 가장자리에서 반응하는 캐릭터 영역으로 변경했습니다.
- Dictionary: 결과 밀도는 유지하면서 정확 일치 강조, 유형 위계, 검색 도구의 표면을 현대적으로 정리했습니다.
- Mobile: 실제 390px 콘텐츠 폭과 일반적인 휴대폰 높이로 다시 확인해 캡처 높이 때문에 생기던 큰 공백을 제거했습니다.

## 최종 polish

- Desktop Today의 학습지와 Haru를 하나의 연속된 학습 면으로 합치고, 콘텐츠 분량보다 컸던 학습지 높이를 줄였습니다.
- Mobile Today의 베이지색 캐릭터 배너를 제거하고 Haru가 학습지 가장자리에서 반응하는 형태로 정리했습니다.
- `WORD / GRAMMAR` 표시를 `단어 / 문법`으로 바꾸고 작은 라벨, 링크, 버튼 전환과 구분선의 위계를 다듬었습니다.
- 승인된 Desktop Home과 Dictionary의 정보 구조 및 밀도는 유지했습니다.
