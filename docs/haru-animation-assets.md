# Haru 성장 시스템 V2 asset contract

이 문서는 현재 실행 계약이다. 이전 디자인의 Blink 검수 이력은 `current-features.md`의 과거 기록에만 남긴다. 이미지 생성·편집·crop·색 변경·변환 없이 제공된 파일을 그대로 사용한다.

## 도메인과 이미지

| Semantic key | UI/manifest key | 누적 EXP | 기본 poster |
| --- | --- | ---: | --- |
| young | stage-1 | 0 | haru-stage-1.png |
| apprentice | stage-2 | 300 | haru-stage-2.png |
| confident | stage-3 | 1000 | haru-stage-3.png |
| reliable | stage-4 | 2500 | haru-stage-4.png |

기존 enum과 API stageKey는 유지한다. JLPT는 성장에 영향을 주지 않는다. `HaruPresentationService`는 enum 계산을 재사용하며 `HaruAssetResolver`는 도메인 stage를 바꾸지 않고 존재하는 가장 가까운 이전 poster를 선택한다. 현재 Stage 1~3만 설치되어 Stage 4는 Stage 3 poster를 사용한다. 모든 이전 poster도 없다면 path는 null이고 template은 이미지 요청 없이 기존 text fallback을 사용한다.

파일 디렉터리는 `/images/characters/haru`다. `GET /images/characters/haru/animation.json`은 정적 파일이 아니라 resolver가 제공하는 manifest v2다. Controller와 template은 파일명을 조합하지 않는다. 실제 asset 추가는 애플리케이션 resource 배포와 함께 진행한다.

## 선택적 상태와 Blink

상태 poster 계약은 `haru-stage-{n}-{state}.png`이며 state는 idle/study/happy/goal-complete/growth다. 기본 poster가 정규 idle/reduced-motion 기준이다. 상태 poster가 없으면 같은 asset stage의 기본 poster로 CSS motion을 수행한다.

Blink frame 계약은 `haru-stage-{n}-blink-01.png`, `-02.png`, `-03.png`다. 세 파일이 모두 존재할 때만 manifest에 frames와 ambient Blink 항목을 넣는다. 현재 새 디자인의 Blink frame은 없으므로 모든 stage의 supportsBlink=false이며 frame을 요청하지 않는다. 이전 디자인의 frame을 새 디자인에 섞지 않는다.

지원되는 경우 순서는 poster → 01 → 02 → 03 → poster, hold는 90/70/110/70/120ms다. 반감김/완전감김/복귀 반감김은 70/110/70ms를 유지한다. idle ambient는 8~16초 무작위 간격, breathe 4·settle 1·지원되는 Blink 2의 가중치를 사용한다.

`character-animator.js`는 공통 timer, run id, state 전환 cancel, hidden 중지, reduced-motion을 유지한다. reduced-motion에서는 기본 poster만 표시하고 Blink frame을 접근하지 않는다. 성장 연출은 API가 한 번만 확보한 이벤트에만 실행한다. hidden/reduced-motion 전환 후 이미 확보한 성장 연출은 다시 실행하지 않는다.

## 성장 이벤트와 호환성

기존 `LearnerProfile.pendingGrowthStageKey`를 재사용하고 nullable `presentedGrowthStageKey`만 추가한다. EXP 지급 직전/직후 stage가 달라질 때만 pending을 기록한다. 기존 사용자에게 과거 성장 이벤트를 생성하지 않는다. 기존 pending이 있다면 최신 단계 안내 한 건만 유지한다.

`POST /api/v1/characters/growth/present?stageKey=...`는 현재 사용자의 Profile을 잠그고 한 요청에만 claimed=true를 돌려준다. 응답 전에 연출 표시 여부를 저장하므로 refresh/back/동시 기기에서 중복 연출되지 않는다. 네트워크 단절로 확보 응답이 유실되면 연출을 다시 보장할 수는 없지만, 별도 성장 안내는 명시적으로 확인할 때까지 남는다. 여러 경계를 보지 않고 지나면 최신 성장 한 건으로 합친다.

`POST /api/v1/characters/growth/acknowledge?stageKey=...`는 일치하는 pending만 지운다. 오래된 stage 확인 요청은 최신 이벤트를 지우지 않는다. 기존 stageKey 없는 acknowledge endpoint는 API 호환 목적으로 유지한다. 모든 개인 API는 현재 인증 계정 기준이며 CSRF를 유지한다.

## 검증 방법

Java 테스트는 threshold 경계, final stage, domain/asset 분리, 설치 파일, 없는 frame, null 호환성, Today 중복 완료, Mission/Quiz 정책, 사용자 격리와 CSRF를 검증한다. 브라우저 lifecycle 테스트의 가상 frame URL은 실제 poster 바이트를 그대로 응답하는 테스트 fixture이며 실제 Blink 그림이 준비됐다는 뜻이 아니다. 이미지 시각 검수 없이 DOM·state·network·timer만 검증한다.
