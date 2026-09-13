# UI 리서치와 적용 판단

## 참고한 제품

| 제품 | 확인한 패턴 | Japanese에 적용한 판단 |
|---|---|---|
| Duolingo | 홈에서 다음 행동을 하나로 좁히는 학습 경로, 모바일 핵심 탭 | 홈의 주 CTA를 `오늘 학습 이어서`로 한정했습니다. 게임 지도와 보상 HUD는 가져오지 않았습니다. |
| Busuu | 목표 기반 학습 계획, 문법 복습을 독립된 학습 도구로 연결 | 오늘 계획과 설정값의 관계를 짧게 보여주고 복습 진입점을 분명히 했습니다. |
| Bunpro | SRS 복습 대기량과 문법 학습의 직접 연결 | 복습을 새 학습보다 앞에 배치하고 단어·문법 수를 한 줄에서 구분했습니다. |
| LingoDeer | 언어 콘텐츠 중심 SRS와 복습 | 학습 카드에서 진행 UI보다 일본어 본문과 예문을 우선했습니다. |
| Quizlet | 학습 진척과 취약 항목을 다음 행동으로 연결 | 약점·기록·리포트를 `내 학습` 아래의 후속 행동으로 묶었습니다. |
| Takoboto | 빠르게 훑을 수 있는 일본어 사전 결과 | 사전을 독립 도구로 두고 결과를 행 단위의 고밀도 목록으로 설계했습니다. |
| AnkiMobile | 답변 전 콘텐츠 집중, 하단의 안정적인 응답 동작 | Today의 답변 영역을 하단에 고정하고 현재 카드의 정보 위계를 단순화했습니다. |
| Renshuu | 사전과 학습의 연결, 약한 콘텐츠 우선 복습 | 검색 결과에서 저장·학습 상태를 짧게 표시하고 사전에서 학습 흐름으로 이어질 자리를 마련했습니다. |
| Finch | 사용자의 행동과 동반자 상태가 연결되는 홈 | Haru를 오늘 학습과 같은 장면 안에 두되 상점·퀘스트 같은 게임 구조는 제외했습니다. |

## 버린 패턴

- 같은 크기의 통계 카드가 반복되는 대시보드
- 숫자 하나를 위해 화면을 크게 쓰는 KPI 카드
- 홈을 검색, 통계, 콘텐츠 추천으로 가득 채우는 구성
- 과한 둥근 모서리, 그림자, 그라데이션, pill UI
- 캐릭터를 프로필 아이콘으로 축소하거나 반대로 학습 정보를 밀어내는 큰 장식 영역
- RPG HUD, 상점, 퀘스트, 과도한 연속 보상
- 데스크톱을 그대로 세로로 쌓은 모바일 화면

## 결정한 정보 구조

주 내비게이션은 `홈 / 오늘 / 사전 / 복습 / 내 학습`입니다.

- 홈: 오늘 할 일과 Haru 성장 상태를 한 장면으로 연결
- 오늘: 고정된 일일 세션의 이어하기와 완료
- 사전: 검색, 필터, 결과 스캔, 상세 진입
- 복습: SRS 대기와 자유·약점 재학습
- 내 학습: 진도, 기록, 주간 리포트, 북마크, 컬렉션, 설정

데스크톱은 좌측 레일로 탐색과 보조 기능을 함께 노출합니다. 모바일은 다섯 개의 하단 탭만 유지하고 세부 기능은 `내 학습` 안에서 펼치는 구조입니다.

## 시각 방향

작업대 같은 짙은 남색 내비게이션, 종이 같은 중립 배경, 행동을 나타내는 절제된 적갈색, 학습 진도를 나타내는 녹색을 사용했습니다. Haru 공간만 따뜻한 목재색을 사용해 콘텐츠 영역과 연결하면서도 성격을 구분했습니다. 대부분의 그룹은 카드 대신 구분선, 열, 배경 면으로 조직했습니다.

EXP와 Haru 성장은 캐릭터 축으로, JLPT 학습 진도는 숙련도 축으로 분리합니다. Haru 영역은 고정 이미지가 아니라 `character-stage`, `character-asset`, `character-reaction` 층으로 나누어 실제 애니메이션 자산 교체에 대비했습니다.

## 참고 링크

- Duolingo home redesign: https://blog.duolingo.com/new-duolingo-home-screen-design/
- Duolingo core tabs: https://blog.duolingo.com/core-tabs-redesign/
- Busuu study plan: https://www.busuu.com/en/english/personalized-study-plan-busuu-premium
- Busuu grammar review: https://blog.busuu.com/grammar-review-web-release/
- Bunpro FAQ: https://community.bunpro.jp/t/bunpro-faq-frequently-asked-questions/876
- Quizlet Progress: https://help.quizlet.com/hc/en-us/articles/360048803491-Using-Progress-for-targeted-studying
- LingoDeer SRS: https://lingodeer.freshdesk.com/support/solutions/articles/61000319118-how-does-the-spaced-repetition-system-srs-work-
- Takoboto: https://play.google.com/store/apps/details?id=jp.takoboto
- AnkiMobile study screen: https://docs.ankimobile.net/study-screen.html
- Renshuu: https://apps.apple.com/us/app/renshuu-japanese-learning/id1542730063
- Finch home: https://help.finchcare.com/hc/en-us/articles/37780000231309-Exploring-the-Finch-Home-Page
