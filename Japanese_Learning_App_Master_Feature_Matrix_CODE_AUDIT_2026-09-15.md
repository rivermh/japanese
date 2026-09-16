# Japanese Learning App — Master Feature Matrix
기준일: 2026-09-15

이 문서는 구현하면서 체크를 지워 나가는 **실전 백로그**입니다.

## 범례
- `[x]`: 현재 코드에서 사용자 흐름까지 구현된 기능
- `[~]`: 부분 구현. 백엔드/데이터/구성요소는 있으나 중요한 UX·범위·운영 조건 중 일부가 남은 기능
- `[ ]`: 현재 코드에서 구현을 확인하지 못한 기능
- 경쟁사: `✅` 명확히 제공 / `△` 부분 제공·유사 기능 / `—` 핵심 기능으로 확인되지 않음
- `P0`: V1 공개 전 반드시 끝내야 하는 핵심 학습·데이터 안전·계정/운영 항목
- `P1`: V1 직후 학습 품질과 경쟁력을 크게 높이는 항목
- `P2`: 제품 확장/고도화 항목
- `P3`: 현재 제품 방향에서는 의도적으로 후순위

### 코드 감사 기준
- 기준 브랜치: `ui/japanese-ui-consolidation`
- 기준 HEAD: `a0006565a2ff54b221f372455e32f5d4dfd49e53` (`fix(core): harden phase 0 learning infrastructure`)
- Phase 0 HEAD는 production baseline이며, Phase 1A Ticket A~F의 source rights, Release Gate, publication safety, dry-run, batch execution/immutable manifest 및 Admin Batch UX는 현재 미커밋 working tree에 반영되어 있다.
- 상태 판정은 엔티티/서비스/API/템플릿/CSS·JS/테스트/현재 기능 문서를 함께 보고 **실제로 사용자 흐름으로 이어지는지**를 우선했습니다.
- 최신 UI 통합에서 authenticated 상단 navigation, Guest Index, Login, Home, `/today` recall player, `/haru` 전용 화면과 정보 페이지 정렬이 반영된 상태를 기준으로 재검사했습니다.
- 콘텐츠 import가 존재하더라도 대량 데이터가 PENDING·비공개이면 전체 코스 관점에서는 `[~]`로 표시했습니다.

> 주의: `—`는 “절대 존재하지 않는다”가 아니라, 2026-09-15 기준 공식 설명/문서에서 해당 기능을 핵심 제공 기능으로 확인하지 못했다는 뜻입니다.

## 벤치마크 앱
- iroiro JLPT — 단어/한자/FSRS/쓰기 중심
- renshuu — 일본어 종합 학습 + SRS + 강한 게임화/캐릭터
- Anki — 범용 플래시카드/FSRS의 기준
- Migii JLPT — JLPT 실전 시험/로드맵의 기준
- Duolingo — 습관/게임화/코스 진행의 기준
- Bunpro — 일본어 문법/SRS/문맥 학습의 기준


## 콘텐츠/데이터
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [~] | P0 | JLPT N5~N1 레벨별 어휘 | ✅ | ✅ | △ | ✅ | △ | ✅ | 현재: N5~N1 import WORD 9,159건이 있고 사용자 공개는 승인된 8건뿐이다. 나머지 9,151건은 PENDING·비공개라 출시 체급으로는 부분 완료. / 기존 메모: 출시 기본 체급 |
| [~] | P0 | 단어 뜻·읽기·품사·후리가나 | ✅ | ✅ | △ | ✅ | ✅ | ✅ | 현재: 뜻·읽기·품사와 상세 표시는 구현. 읽기는 제공하지만 독립적인 ruby/후리가나 제어 기능까지는 확인되지 않음. / 기존 메모: 단어 상세 기본 |
| [x] | P0 | 예문 + 번역 | ✅ | ✅ | △ | ✅ | ✅ | ✅ | 현재: Example에 일본어·읽기·번역이 있고 단어/문법 상세에서 표시. / 기존 메모: 콘텐츠 품질 중요 |
| [ ] | P1 | 단어/예문 음성 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 음성/TTS 재생 경로를 확인하지 못함. / 기존 메모: 가능하면 네이티브, 초기엔 고품질 TTS도 현실적 |
| [ ] | P1 | 단어 활용형/동사·형용사 활용 | ✅ | ✅ | △ | ✅ | ✅ | ✅ | 현재: 활용형 전용 모델/표시 기능을 확인하지 못함. / 기존 메모: iroiro 0.39.x에서 강화 |
| [ ] | P2 | 유의어·반의어·동사쌍·관련어 | ✅ | ✅ | △ | △ | △ | △ | 현재: GrammarRelation은 있으나 단어 유의어·반의어·동사쌍용 관계 모델은 확인되지 않음. / 기존 메모: 단어 상세 깊이 |
| [ ] | P2 | 동음이의/동음어·복합어 | ✅ | ✅ | △ | △ | △ | △ | 현재: 전용 관계/탐색 기능을 확인하지 못함. / 기존 메모: 검색/연결 탐색에 유용 |
| [x] | P1 | 억양/피치 악센트 | ✅ | ✅ | △ | — | — | — | 현재: Word.pitchAccent와 상세 표시/숫자 피치 해석 경로가 구현됨. / 기존 메모: 고급 학습자 가치 큼 |
| [ ] | P2 | 히라가나/가타카나 학습 | ✅ | ✅ | △ | ✅ | ✅ | — | 현재: 검색 정규화는 있으나 문자 학습 코스는 없음. / 기존 메모: 초보 유입용 |
| [~] | P0 | 문법 N5~N1 | △ | ✅ | △ | ✅ | ✅ | ✅ | 현재: GRAMMAR 3,605건과 레벨 관계/상세/예문 구조가 있고 사용자 공개는 승인된 5건뿐이다. 나머지 3,600건은 PENDING·비공개라 전체 사용자 노출은 부분 완료. / 기존 메모: 앱 범위를 넓힐 때 |
| [ ] | P2 | 독해 콘텐츠 | — | ✅ | △ | ✅ | ✅ | ✅ | 현재: 독해 전용 콘텐츠/세션 확인되지 않음. / 기존 메모: V2 확장 후보 |
| [ ] | P2 | 듣기 콘텐츠 | △ | ✅ | △ | ✅ | ✅ | △ | 현재: 듣기 전용 콘텐츠/세션 확인되지 않음. / 기존 메모: JLPT 대비 확장 |

## 검색/단어장
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [x] | P0 | 일본어/한국어 단어 검색 | ✅ | ✅ | ✅ | ✅ | △ | ✅ | 현재: 표기·읽기·한국어 뜻·문법·예문 검색과 정규화/필터가 구현됨. / 기존 메모: 핵심 |
| [ ] | P2 | 로마자 검색 | ✅ | ✅ | △ | △ | △ | — | 현재: 현재 코드가 의도적으로 지원하지 않는다고 문서화되어 있음. / 기존 메모: 초보 UX |
| [x] | P0 | 검색 결과에서 단어 상세 진입 | ✅ | ✅ | ✅ | ✅ | △ | ✅ | 현재: Dictionary 결과에서 content detail로 진입. / 기존 메모: 핵심 |
| [x] | P0 | 즐겨찾기/개인 단어장 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: Bookmark 및 저장 화면/재학습 경로가 존재. / 기존 메모: 핵심 |
| [x] | P1 | 사용자 커스텀 덱/리스트 | ✅ | ✅ | ✅ | △ | — | ✅ | 현재: StudyCollection 기반 사용자 컬렉션이 존재. / 기존 메모: 장기 사용성 |
| [~] | P1 | 검색 결과를 바로 학습/저장 | ✅ | ✅ | ✅ | △ | — | ✅ | 현재: 검색 결과는 상세로 이동하고 상세에서 북마크·학습 큐 추가가 가능하다. 검색 결과 row 자체의 바로 학습/저장 액션은 없다. / 기존 메모: 마찰 감소 |
| [~] | P1 | Archive/학습 제외/복구 | ✅ | ✅ | ✅ | — | — | △ | 현재: SUSPENDED 학습 상태는 존재하지만 완전한 사용자용 Archive/복구 UX는 확인되지 않음. / 기존 메모: 카드 관리 |
| [~] | P1 | 뜻 숨기기 셀프테스트 | ✅ | ✅ | ✅ | — | — | ✅ | 현재: 복습 Focus 흐름에 prompt→reveal 자기회상은 있으나 범용 단어장 '뜻 숨기기' 기능은 아님. / 기존 메모: 가벼운 복습 |
| [~] | P3 | 외부 단어 일괄 가져오기/CSV | △ | ✅ | ✅ | — | — | — | 현재: APKG import 파이프라인은 있으나 사용자용 CSV/외부 단어 import 기능은 아님. / 기존 메모: iroiro 리뷰에서 불편 지적된 틈새 |
| [ ] | P1 | 콘텐츠 오류 신고 | ✅ | ✅ | — | △ | △ | △ | 현재: 관리자 품질/검수 시스템은 있으나 사용자 오류 신고 흐름은 확인되지 않음. / 기존 메모: 콘텐츠 규모 커지면 필수 |

## 학습/복습
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [x] | P0 | JLPT 레벨별 덱/코스 | ✅ | ✅ | △ | ✅ | △ | ✅ | 현재: LearnerStudyPreference의 JLPT scope와 레벨별 학습/진도 경로가 구현됨. / 기존 메모: 핵심 |
| [x] | P0 | 신규 학습 + 복습 분리 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: Today 계획에서 REVIEW와 NEW WORD/GRAMMAR가 분리됨. / 기존 메모: 핵심 |
| [x] | P0 | 오늘의 학습 목표 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 일일 목표/Today snapshot/DailyMission이 구현됨. / 기존 메모: 홈 핵심 |
| [x] | P0 | 간격 반복(SRS) | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: LEARNING/REVIEW/MASTERED/SUSPENDED와 1·2·4·8·16일 간격, 오답 10분 재복습이 구현됨. / 기존 메모: 필수 |
| [~] | P1 | FSRS 또는 동급 스케줄러 | ✅ | △ | ✅ | — | — | — | 현재: 동작하는 자체 SRS는 있으나 FSRS/기억확률 기반 스케줄러는 아님. / 기존 메모: 우리도 FSRS 권장 |
| [x] | P0 | 오늘 복습 자동 큐 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: due review 우선 Today plan과 persisted session이 구현됨. / 기존 메모: 필수 |
| [x] | P0 | 오답 재학습/재등장 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 오답 시 LEARNING으로 복귀하고 10분 뒤 재복습, 약점 재학습도 존재. / 기존 메모: 필수 |
| [x] | P1 | 일일 신규 카드 수 설정 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: 단어/문법 신규 일일 제한을 사용자별 저장. / 기존 메모: 부담 조절 |
| [ ] | P2 | 일일 오답/재시도 카드 제한 | ✅ | ✅ | ✅ | — | — | ✅ | 현재: 신규 제한과 전체 일일 목표는 있으나 오답/재시도 전용 제한 설정은 확인되지 않음. / 기존 메모: 과부하 방지 |
| [~] | P1 | Extra Study/오늘만 추가 학습 | ✅ | ✅ | ✅ | △ | ✅ | ✅ | 현재: 정규 Today 외 자유 Study/재학습 경로는 있으나 '오늘만 +N' 형태의 명시적 extra quota는 없음. / 기존 메모: 학습 의욕 활용 |
| [x] | P1 | Pick & Study/선택 카드만 학습 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: 컬렉션/학습 대기열 및 선택 학습 기반이 구현됨. / 기존 메모: 커스텀 복습 |
| [~] | P1 | 과거 학습 기록에서 재학습 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: 학습 이력과 북마크/약점 재학습은 있으나 과거 기록 임의 선택→재학습의 완전한 직접 흐름은 제한적. / 기존 메모: 오답 회고 |
| [ ] | P1 | 이미 아는 카드 표시/건너뛰기 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: Known/skip-as-known 전용 동작을 확인하지 못함. / 기존 메모: 초기 레벨 테스트와 연결 |
| [~] | P1 | 복습 카드 Skip/일시 제외 | ✅ | ✅ | ✅ | — | — | △ | 현재: SUSPENDED 상태 기반은 있으나 복습 화면에서의 완전한 skip/restore UX는 확인되지 않음. / 기존 메모: 사용자 통제 |
| [x] | P0 | 중단한 학습/퀴즈 이어하기 | ✅ | ✅ | ✅ | △ | ✅ | ✅ | 현재: TodayStudySession persisted snapshot과 QuizSession startOrResume이 구현됨. / 기존 메모: 모바일 필수 |
| [x] | P0 | 학습 중 신규/복습/완료 카운터 | ✅ | ✅ | ✅ | △ | ✅ | ✅ | 현재: Today session view/DailyMission에 review/new/completed 카운트가 있음. / 기존 메모: 진행감 |
| [ ] | P2 | 학습일 리셋 시간 사용자 설정 | ✅ | △ | ✅ | — | △ | △ | 현재: Asia/Seoul 기준은 있으나 사용자별 day-boundary 설정은 없음. / 기존 메모: 야간 학습자 |
| [ ] | P2 | 개인 기억 데이터로 스케줄 최적화 | △ | ✅ | ✅ | — | — | △ | 현재: 개인별 progress는 저장하지만 기억확률 기반 최적화는 없음. / 기존 메모: 고급 개인화 |

## 퀴즈
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [x] | P1 | 뜻 맞히기 객관식 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: WORD_JAPANESE_TO_MEANING 유형 존재. / 기존 메모: 필수 |
| [x] | P1 | 읽기 맞히기 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: WORD_READING_CHOICE/WORD_READING_INPUT 유형 존재. / 기존 메모: 필수 |
| [x] | P1 | 일→한/한→일 양방향 | ✅ | ✅ | ✅ | △ | ✅ | ✅ | 현재: WORD_JAPANESE_TO_MEANING / WORD_MEANING_TO_JAPANESE 양방향 유형 존재. / 기존 메모: 필수 |
| [~] | P1 | 타이핑 정답 입력 | ✅ | ✅ | ✅ | △ | ✅ | ✅ | 현재: 읽기 입력형은 있으나 범용 의미/표기 타이핑 유형까지는 없음. / 기존 메모: 회상 강도 상승 |
| [ ] | P2 | 듣고 단어/문장 맞히기 | △ | ✅ | ✅ | ✅ | ✅ | △ | 현재: 오디오 기반 퀴즈 없음. / 기존 메모: 청해 확장 |
| [~] | P2 | 문장 빈칸 채우기 | △ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: GRAMMAR_CONTEXT_CHOICE는 있으나 완전한 입력형 빈칸 채우기와는 다름. / 기존 메모: 문맥 학습 |
| [~] | P1 | 퀴즈 유형/문항 수 설정 | ✅ | ✅ | ✅ | ✅ | △ | ✅ | 현재: 문항 수 요청 모델은 1~20 지원하지만 현재 QuizMode는 QUICK 하나이고 UI 시작은 5문항 고정. / 기존 메모: 사용자 통제 |
| [ ] | P1 | 카드 상태/오답만 퀴즈 필터 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: 퀴즈 시작 시 카드 상태/오답 전용 필터를 확인하지 못함. / 기존 메모: 집중 복습 |
| [~] | P1 | 퀴즈 이력/정오답 기록 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: QuizSession/QuizAttempt와 이력 화면은 동작한다. QuizAttempt provenance는 IMPORTED_SOURCE/QUIZ_SESSION_ITEM/LEGACY_UNKNOWN 및 nullable FK로 정규화됐지만, 사용자용 퀴즈 이력 상세 UX는 제한적이다. / 남은 것: 이력에서 문제 원본과 세션 맥락을 안정적으로 탐색하는 전용 UX. / 기존 메모: 통계와 연결 |
| [ ] | P3 | 타임어택/스피드 퀴즈 | — | ✅ | — | ✅ | ✅ | — | 현재: 시간 제한/스피드 모드 없음. / 기존 메모: 게임성 |
| [ ] | P2 | 실전형 JLPT 문항 | — | △ | — | ✅ | — | △ | 현재: 현재 퀴즈는 학습 콘텐츠 기반 확인 문제이며 실전 시험 문항 시스템은 아님. / 기존 메모: 시험 대비 확장 |

## 한자
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [ ] | P1 | 한자 상세: 뜻/음독/훈독 | ✅ | ✅ | △ | ✅ | ✅ | △ | 현재: 독립 KanjiEntry/한자 상세 도메인 없음. / 기존 메모: 필수 |
| [ ] | P1 | 한자와 해당 어휘 연결 | ✅ | ✅ | △ | ✅ | ✅ | △ | 현재: 한자 엔트리↔어휘 연결 모델 없음. / 기존 메모: 단어 앱 핵심 |
| [ ] | P2 | 부수/구성 요소 분해 | ✅ | ✅ | △ | △ | ✅ | — | 현재: 미구현. / 기존 메모: iroiro 핵심 |
| [ ] | P2 | 유사 한자 비교 | ✅ | ✅ | △ | △ | ✅ | — | 현재: 미구현. / 기존 메모: 혼동 방지 |
| [ ] | P2 | 한자 선행 워밍업 | ✅ | ✅ | — | △ | △ | — | 현재: 미구현. / 기존 메모: 단어 전 한자 노출 |
| [ ] | P2 | 획순 애니메이션 | ✅ | ✅ | △ | △ | ✅ | — | 현재: 미구현. / 기존 메모: 쓰기와 결합 |
| [ ] | P2 | 손글씨/쓰기 연습 | ✅ | ✅ | △ | △ | ✅ | — | 현재: 미구현. / 기존 메모: 생산 연습 |
| [ ] | P3 | 부수 조각 퍼즐 | ✅ | △ | — | — | — | — | 현재: 미구현. / 기존 메모: iroiro parity |
| [ ] | P3 | 상용한자/Jōyō 별도 코스 | ✅ | ✅ | △ | △ | — | — | 현재: 미구현. / 기존 메모: 고급 코스 |
| [ ] | P2 | 빈도/학교급/분류 라벨 | ✅ | ✅ | △ | △ | — | — | 현재: 한자 메타데이터 도메인 없음. |
| [ ] | P2 | 그림/스토리 mnemonic | ✅ | ✅ | — | — | — | — | 현재: 한자 mnemonic 콘텐츠 시스템은 아직 없음. / 기존 메모: 제작비 매우 큼 |
| [ ] | P2 | 선행지식 기반 한자→단어 순서 | △ | ✅ | — | — | △ | — | 현재: 선행지식 그래프/한자 도메인 없음. / 기존 메모: 우리 차별화 후보 |

## JLPT 시험
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [ ] | P2 | 초기 실력/레벨 진단 | — | ✅ | — | ✅ | ✅ | △ | 현재: Onboarding/학습 범위 선택은 있으나 진단 테스트는 아님. / 기존 메모: 온보딩 개인화 |
| [~] | P2 | 개인 학습 로드맵 | △ | ✅ | — | ✅ | ✅ | ✅ | 현재: 학습 범위·다음 행동·주간 추천은 있으나 장기 개인 로드맵 엔진은 아님. / 기존 메모: 무엇을 공부할지 자동 결정 |
| [ ] | P2 | N5~N1 실전 모의고사 | — | △ | — | ✅ | — | — | 현재: 미구현. / 기존 메모: Migii 강점 |
| [ ] | P2 | 시간 제한 실전 모드 | — | △ | — | ✅ | ✅ | — | 현재: 미구현. / 기존 메모: 시험 대비 |
| [~] | P2 | 문자·어휘/문법/독해/청해 섹션 연습 | △ | ✅ | — | ✅ | △ | ✅ | 현재: 어휘/문법 학습·퀴즈는 있으나 독해/청해/시험 섹션 구조는 없음. / 기존 메모: 범위 확장 |
| [~] | P2 | 정답 상세 해설 | △ | ✅ | — | ✅ | △ | ✅ | 현재: 콘텐츠 설명/예문은 있으나 실전 문항별 상세 해설 시스템은 없음. / 기존 메모: 콘텐츠 비용 큼 |
| [x] | P1 | 약점 자동 분석/추천 | △ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 최근/반복 오답 기반 WeaknessNote, focused review, 주간 추천이 구현됨. / 기존 메모: 통계 활용 |
| [ ] | P3 | 온라인 모의고사/전세계 랭킹 | — | △ | — | ✅ | ✅ | — | 현재: 미구현. / 기존 메모: 초기엔 불필요 |

## 통계/진도
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [x] | P0 | JLPT 레벨별 진도율 | ✅ | ✅ | △ | ✅ | ✅ | ✅ | 현재: `/progress`의 공개 콘텐츠 기준 레벨별 progress map과 통계/주간 진도 변화가 구현됨. due 요약은 published 및 SUSPENDED 제외 기준을 사용한다. / 기존 메모: 핵심 |
| [x] | P0 | 일일/주간 학습 기록 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 일일 progress, history, 최근 7일 및 Weekly Report가 구현됨. / 기존 메모: 핵심 |
| [x] | P0 | 연속 학습 Streak | ✅ | ✅ | △ | ✅ | ✅ | ✅ | 현재: 현재/최고 streak와 학습일 계산이 구현됨. / 기존 메모: 리텐션 핵심 |
| [~] | P1 | 활동 캘린더 | ✅ | ✅ | ✅ | △ | ✅ | ✅ | 현재: 최근 7일 activity 뷰는 있으나 월간/GitHub 잔디형 캘린더는 아님. / 기존 메모: GitHub 잔디 느낌 |
| [ ] | P2 | 총 학습 시간 | ✅ | ✅ | ✅ | ✅ | △ | ✅ | 현재: 완료 횟수는 추적하지만 실제 학습 duration 누적은 확인되지 않음. / 기존 메모: 진행감 |
| [x] | P1 | 정답률/오답률 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 정답/오답/accuracy 통계가 구현됨. / 기존 메모: 기본 통계 |
| [~] | P1 | 카드별 학습 이력 | ✅ | ✅ | ✅ | △ | — | ✅ | 현재: StudyRecord/학습 이력은 있으나 카드 한 개의 전체 타임라인을 깊게 보는 전용 UX는 제한적. / 기존 메모: 디버깅/학습 모두 유용 |
| [x] | P0 | 기억 상태/숙련도 단계 | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: LEARNING/REVIEW/MASTERED/SUSPENDED 상태와 다음 복습 시각을 표시. / 기존 메모: Haru 성장과 연결 |
| [ ] | P2 | 예상 기억률/retention | △ | ✅ | ✅ | — | — | △ | 현재: 기억확률 모델 없음. / 기존 메모: 고급 통계 |
| [~] | P2 | 시간대별/유형별 성과 분석 | — | ✅ | ✅ | — | — | △ | 현재: 유형별(새 단어/문법/복습/퀴즈) 주간 집계는 있으나 시간대 분석은 없음. / 기존 메모: 고급 통계 |

## 게임화/Haru
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [x] | P1 | 학습 EXP | — | ✅ | — | △ | ✅ | △ | 현재: 정규 학습 결과에 EXP를 지급하고 누적/레벨을 저장. / 기존 메모: 우리 핵심 |
| [x] | P1 | Haru 성장 단계(Stage) | — | ✅ | — | — | △ | — | 현재: CharacterGrowthStage와 EXP 기반 단계 계산/asset resolver, authenticated `/haru` 성장 화면이 구현됨. Stage 4는 Stage 3 asset fallback을 사용한다. / 기존 메모: 우리 핵심 |
| [~] | P2 | 학습 직후 캐릭터 반응 | — | ✅ | — | — | ✅ | — | 현재: 성장 pending/상태 모델과 `/haru`의 성장 notice는 있으나 학습 화면 직후의 별도 캐릭터 반응 UI는 최신 제품 방향에 따라 제공하지 않는다. / 남은 것: 의미 있는 milestone에서 선택적으로 반응을 연결할지 결정. / 기존 메모: 즉시 보상 |
| [ ] | P2 | Streak와 캐릭터 상태 연결 | — | ✅ | — | — | ✅ | — | 현재: Streak 자체는 구현됐지만 캐릭터 상태를 직접 변화시키는 규칙은 확인되지 않음. / 기존 메모: 습관 루프 |
| [~] | P2 | Daily/Weekly Quest | — | ✅ | — | △ | ✅ | — | 현재: DailyMission read model은 구현됐지만 보상형 Quest/Weekly Quest 시스템은 없음. / 기존 메모: 목표 제공 |
| [ ] | P2 | Achievement/업적 | — | ✅ | — | △ | ✅ | — | 현재: 미구현. / 기존 메모: 장기 수집 |
| [ ] | P3 | 코인/재화 | — | ✅ | — | △ | ✅ | — | 현재: 미구현; 현재 제품 방향상 우선순위 낮음. / 기존 메모: 경제 시스템 |
| [ ] | P2 | 성장 보상으로 행동/대사 해금 | — | ✅ | — | — | △ | — | 현재: 단계/이미지는 있으나 행동·대사 unlock 시스템은 없음. / 기존 메모: Haru 차별화 |
| [ ] | P3 | 캐릭터 꾸미기/수집 요소 | — | ✅ | — | — | ✅ | — | 현재: 미구현. / 기존 메모: 과도한 제작량 주의 |
| [ ] | P3 | 방/정원/공간 꾸미기 | — | ✅ | — | — | — | — | 현재: 미구현. / 기존 메모: renshuu Kao Garden 벤치마크 |
| [ ] | P3 | 학습으로 스토리/만화 해금 | — | ✅ | — | — | ✅ | ✅ | 현재: 미구현. / 기존 메모: 장기 보상 |
| [ ] | P3 | 친구/리더보드 | — | △ | — | ✅ | ✅ | — | 현재: 미구현. / 기존 메모: 소셜 |
| [ ] | P3 | 멀티플레이 학습 게임 | — | ✅ | — | ✅ | △ | — | 현재: 미구현. / 기존 메모: 초기엔 불필요 |
| [ ] | P3 | 시즌/이벤트 | — | ✅ | — | ✅ | ✅ | — | 현재: 미구현. / 기존 메모: 운영 역량 필요 |

## UX/개인화
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [ ] | P2 | 다크 모드 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 현재 별도 theme/dark mode 구현을 확인하지 못함. / 기존 메모: 기본 |
| [~] | P0 | 클라우드 동기화/백업 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 계정 기반 서버 DB에 상태가 저장되므로 동기화 기반은 있으나 운영 백업/복구 체계는 별도 확인되지 않음. / 기존 메모: 필수 |
| [x] | P0 | 기기간 이어하기 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 계정 기반 서버 상태 + persisted Today/Quiz session으로 동일 계정 이어하기 가능. / 기존 메모: 필수 |
| [ ] | P1 | 후리가나 표시 on/off | ✅ | ✅ | △ | △ | ✅ | ✅ | 현재: 표시 preference 없음. / 기존 메모: 레벨별 자동도 고려 |
| [ ] | P2 | 뜻/번역 표시 on/off | ✅ | ✅ | ✅ | △ | △ | ✅ | 현재: 복습 reveal은 있으나 사용자 설정형 표시 on/off는 없음. / 기존 메모: 난이도 조절 |
| [ ] | P3 | 로마자 표시 on/off | △ | ✅ | △ | △ | ✅ | — | 현재: 로마자 자체를 현재 의도적으로 지원하지 않음. / 기존 메모: 초보→중급 전환 |
| [ ] | P1 | 글자 크기 설정/큰 글자 대응 | ✅ | ✅ | ✅ | △ | ✅ | ✅ | 현재: responsive CSS는 있으나 사용자 글자 크기 설정은 확인되지 않음. / 기존 메모: 접근성 |
| [ ] | P3 | 홈 섹션 표시/순서 커스터마이즈 | ✅ | ✅ | — | △ | — | △ | 현재: 미구현. / 기존 메모: iroiro 최신 기능 |
| [ ] | P2 | 학습 카드 뒷면 구성 커스터마이즈 | ✅ | ✅ | ✅ | — | — | ✅ | 현재: 미구현. / 기존 메모: 고급 사용자 |
| [ ] | P3 | 효과음/음성 볼륨 분리 | ✅ | ✅ | — | △ | ✅ | — | 현재: 음성/효과음 시스템 자체가 아직 없음. / 기존 메모: 세밀한 UX |
| [~] | P1 | 학습 알림/복습 알림 | △ | ✅ | △ | ✅ | ✅ | ✅ | 현재: reminder enabled/time preference는 저장하지만 실제 notification delivery scheduler/push는 확인되지 않음. / 기존 메모: 리텐션 |
| [ ] | P2 | 오프라인 콘텐츠 | ✅ | △ | ✅ | ✅ | △ | — | 현재: 미구현. / 기존 메모: 여행/통학 |
| [ ] | P3 | 홈/잠금화면 위젯 | ✅ | △ | — | — | ✅ | — | 현재: 모바일 위젯 미구현. / 기존 메모: 재방문 유도 |
| [ ] | P3 | Live Activity/Dynamic Island | ✅ | — | — | — | △ | — | 현재: 미구현. / 기존 메모: iOS 후순위 |

## 운영/품질
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [~] | P0 | 이메일/소셜 로그인 및 계정 복구 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 이메일 가입·검증·비밀번호 재설정은 구현, 소셜 로그인은 확인되지 않음. / 기존 메모: 서비스 기본 |
| [ ] | P0 | 학습 데이터 서버 백업 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: 서버 DB 저장은 구현됐지만 운영 backup/restore 체계는 코드에서 확인되지 않음. / 기존 메모: 유실 금지 |
| [x] | P0 | 콘텐츠 버전/마이그레이션 설계 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: source/version/import 추적과 Flyway V1~V4 versioned migration, runtime Hibernate validate가 구현됨. V3는 source rights, V4는 immutable release batch/manifest를 추가한다. 기존 DB는 명시적 baseline 절차를 사용하며 실제 production rehearsal은 운영 배포 전 작업으로 남아 있다. / 기존 메모: 대규모 단어 DB 필수 |
| [ ] | P3 | 결제 복원/구매 상태 동기화 | ✅ | ✅ | △ | ✅ | ✅ | ✅ | 현재: 결제 시스템 미구현; 유료화 시점에 필요. |
| [ ] | P1 | 사용자 피드백/오류 신고 | ✅ | ✅ | △ | ✅ | △ | ✅ | 현재: 사용자 제출형 피드백/콘텐츠 오류 신고 흐름 확인되지 않음. / 기존 메모: 콘텐츠 개선 루프 |
| [~] | P2 | 학습 이벤트 로그/퍼널 분석 | △ | △ | △ | △ | ✅ | △ | 현재: StudyRecord/QuizAttempt 등 학습 로그는 풍부하지만 제품 analytics funnel/이벤트 계층은 없음. / 기존 메모: 리텐션 개선 |
| [ ] | P0 | 데이터 삭제/계정 탈퇴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 현재: AccountManagementService에서 사용자 셀프 탈퇴/전체 학습 데이터 삭제 경로를 확인하지 못함. / 기존 메모: 정책/스토어 필수 |
| [ ] | P2 | 기능 플래그/점진 배포 | △ | △ | △ | △ | ✅ | △ | 현재: 미구현. / 기존 메모: 업데이트 리스크 감소 |

## 우리만의 승부수
| 우리 | 우선 | 기능 | iroiro | renshuu | Anki | Migii | Duo | Bunpro | 메모 |
|---|---|---|:---:|:---:|:---:|:---:|:---:|:---:|---|
| [x] | P1 | Haru EXP를 단순 클릭이 아닌 '실제 학습'에만 지급 | — | △ | — | — | △ | — | 현재: 정규 학습 answer/complete에 EXP를 연결하고 재학습은 EXP/정규 목표에서 제외해 파밍을 제한. / 기존 메모: 보상 악용 방지 |
| [ ] | P2 | FSRS 복습 성공률/숙련도를 Haru 성장 조건에 반영 | — | △ | — | — | — | — | 현재: Haru 성장 기준은 현재 누적 EXP이며 FSRS/숙련도 조건과 직접 연결되지 않음. / 기존 메모: 학습과 캐릭터를 분리하지 않기 |
| [ ] | P1 | 신규학습보다 '제때 복습'에 더 좋은 보상 | — | △ | — | — | — | — | 현재: 현재 EXP 규칙에 due/on-time review 추가 보상 차등은 확인되지 않음. / 기존 메모: 앱의 교육적 방향 |
| [~] | P2 | Haru 성장 시 외형+행동+대사 모두 변화 | — | △ | — | — | — | — | 현재: Stage/외형 asset은 있으나 행동·대사 변화 시스템은 미완성. / 기존 메모: Stage가 스킨 교체로 끝나면 안 됨 |
| [ ] | P2 | 사용자 JLPT 레벨/학습 이력에 따른 Haru 대사 변화 | — | — | — | — | — | — | 현재: 미구현. / 기존 메모: 개인화 |
| [ ] | P2 | 오늘 복습을 끝내면 Haru의 하루 이벤트 발생 | — | △ | — | — | — | — | 현재: DailyMission 완료는 계산 가능하지만 Haru 이벤트 트리거는 없음. / 기존 메모: Daily loop |
| [ ] | P2 | 단어/한자 선행지식 그래프 기반 추천 순서 | △ | ✅ | — | — | △ | — | 현재: knowledge graph/kanji domain이 아직 없음. / 기존 메모: iroiro 리뷰의 약점 공략 |
| [~] | P2 | Haru와 함께 보는 학습 회고/주간 리포트 | — | △ | — | — | — | — | 현재: `/report/weekly`와 `/haru`가 각각 존재하지만 하나의 Haru 회고 경험으로 연결되지는 않는다. / 남은 것: 주간 성과를 Haru 성장 맥락에서 보여 주는 선택적 회고. / 기존 메모: 통계를 감정적 보상으로 변환 |

## 코드 감사 요약 — 최신 production 기준

- 감사 기준: `ui/japanese-ui-consolidation` / `a0006565a2ff54b221f372455e32f5d4dfd49e53`
- 전체 기능: **125개**
- `[x]` 구현 완료: **30개**
- `[~]` 부분 구현: **29개**
- `[ ]` 미구현/미확인: **66개**
- 우선순위: **P0 25 / P1 36 / P2 46 / P3 18**
- 최신 코드에서 Phase 0의 migration, quiz scope, provenance, due, Today allocation 변경을 반영했다.
- `[ ]`가 많은 것은 한자·시험·게임화·모바일 부가 기능이 포함되어 있기 때문이며, P0의 `[~]`/`[ ]`는 출시 전 별도 확인 대상으로 유지한다.
### 이번 감사의 주요 상태 변경

- `[~] P0` 콘텐츠 버전/마이그레이션 설계 → `[x]`: Flyway V1~V4, Hibernate validate, baseline 절차가 production code와 운영 문서에 반영됨. 실제 production rehearsal은 운영 체크리스트로 분리.
- `[~] P1` 퀴즈 이력/정오답 기록은 `[~]` 유지: provenance namespace 문제는 해결됐지만 사용자용 이력 상세 UX가 제한적임.
- Quiz candidate scope/limit, due/filter consistency, Today word-first allocation, QuizAttempt namespace mixing은 최신 코드에서 **RESOLVED**로 이동.
- 운영 backup 자동화, 계정/학습 데이터 삭제, 공개 콘텐츠 품질/승인, 실제 배포 rehearsal은 여전히 P0 또는 별도 운영 위험으로 유지.

### 이번 감사에서 확인한 구조적 위험

| 항목 | 상태 | 최신 판단 |
|---|---|---|
| QuizAttempt 참조 namespace 혼용 | **RESOLVED** | `QuizAttemptOriginType`, ImportedSourceRecord/QuizSessionItem nullable FK, `LEGACY_UNKNOWN`, V2 backfill과 overlap 보호가 구현됐다. 기존 `question_source_record_id`도 호환용으로 유지된다. |
| Quiz 후보 scope/limit 순서 | **RESOLVED** | published·JLPT·category를 DB에서 먼저 적용한 뒤 `CANDIDATE_LIMIT=500`을 적용한다. EXISTS로 관계 중복을 방지하고 seed ordering을 유지한다. |
| Today 신규 word-first 편향 | **RESOLVED** | `TodayNewContentAllocator`가 due 우선, 최소 보장, 진행률 기반 배분, 후보 부족 재배분을 수행한다. cap, queue, scope, persisted session을 유지한다. |
| Today/Weekly/Statistics due 불일치 | **RESOLVED** | `DueReviewCriteria`/`DueReviewQueryService`가 published·not SUSPENDED·scope·asOf 기준을 공유한다. MASTERED due와 legacy null state도 포함한다. |
| 운영 migration 안전성 | **PARTIALLY RESOLVED** | Flyway V1~V4, `ddl-auto=validate`, clean-disabled, 명시적 기존 DB baseline 및 backup/restore 절차 문서가 있다. 실제 MySQL backup/restore·migration rehearsal과 EXPLAIN은 배포 전 남아 있다. |
| 공개 콘텐츠 승인/품질 | **PARTIALLY RESOLVED** | Source rights, Word/Grammar Release Gate, publication invariant, read-only dry-run, stale digest 검증, atomic batch execution, immutable rollback manifest 및 관리자 개별 확인·실행·이력·rollback UX까지 working tree에 구현됐다. 실제 source license 검증, actual N5 dry-run, duplicate adjudication, production-like rehearsal, N5 Word/Grammar pilot은 남아 있다. |
| 서버 backup/restore 자동화 | **ACTIVE** | 운영 절차 문서는 있으나 scheduler, 자동 backup 검증, 자동 restore 기능은 코드에서 확인되지 않는다. |
| 계정/학습 데이터 삭제 | **ACTIVE** | 셀프 탈퇴와 전체 학습 데이터 삭제 사용자 흐름을 확인하지 못했다. |
| queue가 selected scope를 override하는 정책 | **REVIEW NEEDED** | Today에서 명시적 queue가 일반 level/category scope보다 우선하는 기존 정책이다. 코드 버그로 단정하지 않고 제품 정책 확인 대상으로 남긴다. |
| due/candidate query 운영 성능 | **REVIEW NEEDED** | query는 DB 필터와 제한을 사용하지만 실제 MySQL EXPLAIN 및 대량 데이터 실행 계획은 아직 확인하지 않았다. |

### Phase 0 상태

완료:

- Flyway/versioned migration 및 Hibernate validate 기반
- Quiz candidate scope-first
- QuizAttempt provenance normalization 및 ambiguous overlap 보호
- Due review semantics normalization
- Today balanced word/grammar allocation

아직 남은 P0:

- 대량 PENDING 콘텐츠의 출처·라이선스·품질 검수와 공개 정책
- 운영 DB backup/restore 실행 및 rehearsal
- 계정 탈퇴와 학습 데이터 삭제
- 이메일 계정 복구의 production 운영 검증 및 social login 여부 결정
- 핵심 사용자 흐름의 실제 E2E/mobile/대량 콘텐츠 검증

### 최신 코드 검증 기록

- 기준 commit: `a0006565a2ff54b221f372455e32f5d4dfd49e53` (`fix(core): harden phase 0 learning infrastructure`)
- 전체 Gradle 테스트: **180 tests, failures 0, errors 0, skipped 1** (Ticket F, 2026-09-16). 별도 JavaScript 안전 UX 테스트 9건 통과, synthetic Chrome 1024px/390px 검수 통과.
- V2 ambiguous overlap targeted migration test 통과
- production source는 수정하지 않았으며 이 Matrix 문서만 갱신한다.

## Phase 1 추천 우선순위 — dependency 기준

제품 우선순위는 `콘텐츠 신뢰성 > 회상/복습 품질 > 학습 흐름 UX > 통계/개인화 > Haru 보상 > 부가 게임화`를 유지한다.

### Phase 1A — 콘텐츠 사용 가능성/품질

#### Ticket 1A-1: 공개 콘텐츠 승인·품질 게이트
- 목표: PENDING 콘텐츠를 출처·라이선스·필수 필드·중복·예문 품질 기준으로 검수해 실제 공개 가능한 JLPT 체급을 만든다.
- 현재 상태: 미커밋 working tree에서 A Source Rights, B Release Gate, C Publication Safety, D Dry-run, E Batch Execution/History, F Admin Batch UX infrastructure 구현 완료. 실제 source license verification, actual N5 dry-run, duplicate adjudication, production-like rehearsal, N5 Word pilot 및 Grammar pilot은 남아 있어 1A-1 전체 완료 상태는 아니다.
- 예상 영역: content review service/controller, import validator, quality issue 모델/테스트, 운영 문서.
- 선행 dependency: 없음. Phase 0 migration 완료 후 진행.
- 난이도: Large / 추천 모델: Sol High.

#### Ticket 1A-2: 사용자 콘텐츠 오류 신고
- 목표: 사용자 신고를 저장하고 관리자 review queue와 연결한다.
- 현재 상태: 관리자 검수는 있으나 사용자 제출형 오류 신고 flow는 없다.
- 예상 영역: report entity/repository/service/controller, moderation UI, notification/analytics hook.
- 선행 dependency: 1A-1의 품질 상태/운영 정책.
- 난이도: Medium / 추천 모델: Sol Medium.

### Phase 1B — 학습 카드 품질

#### Ticket 1B-1: 단어·예문 음성
- 목표: 일본어 표현과 대표 예문의 재생을 제공해 입력/청해 전 단계의 학습 품질을 높인다.
- 현재 상태: audio_file_name 필드는 있으나 사용자 재생 경로/TTS delivery는 확인되지 않았다.
- 예상 영역: audio asset/provider abstraction, content DTO/template, caching 및 접근성.
- 선행 dependency: 공개 콘텐츠 품질 게이트.
- 난이도: Medium / 추천 모델: Luna High.

#### Ticket 1B-2: 활용형/문법 예문 보강
- 목표: 동사·형용사 활용과 대표 문맥을 연결한다.
- 현재 상태: Word/Grammar 기본 설명·예문은 있으나 conjugation domain이 없다.
- 예상 영역: conjugation model/import, detail/Today presentation, test fixtures.
- 선행 dependency: 1A-1.
- 난이도: Large / 추천 모델: Sol High.

### Phase 1C — 복습 제어

#### Ticket 1C-1: Archive/Suspend·Known/Skip UX
- 목표: 사용자가 이미 아는 카드, 잠시 제외할 카드, 복습 복귀 카드를 명시적으로 관리한다.
- 현재 상태: SUSPENDED 상태와 일부 API/재학습 경로는 있으나 완성된 사용자 제어 UX는 제한적이다.
- 예상 영역: Today/Study UI, learning status endpoint, state transition tests.
- 선행 dependency: Phase 0 due semantics.
- 난이도: Medium / 추천 모델: Luna High.

#### Ticket 1C-2: 복습 제어와 재시도 정책
- 목표: review-only, skip, relearn, retry 흐름의 사용자 의미를 명확히 한다.
- 현재 상태: Today/Study/Weakness 흐름은 동작하지만 세밀한 review controls는 부분 구현이다.
- 예상 영역: LearningService policy layer, templates/API, regression tests.
- 선행 dependency: 1C-1.
- 난이도: Medium / 추천 모델: Sol Medium.

#### Ticket 1C-3: 검색 결과에서 바로 저장/학습
- 목표: 발견한 표현을 detail을 거치지 않고 bookmark/collection/today 학습으로 연결한다.
- 현재 상태: 검색·상세·bookmark/collection route는 있으나 결과 row 직접 action은 제한적이다.
- 예상 영역: Dictionary template/controller, bookmark/collection links, accessibility tests.
- 선행 dependency: 1A-1.
- 난이도: Small / 추천 모델: Luna High.

### Phase 1D — Quiz 강화

#### Ticket 1D-1: 퀴즈 타이핑·오답/상태 필터
- 목표: typing question과 wrong-only/status filter로 recall 품질을 높인다.
- 현재 상태: reading input과 여러 객관식 유형은 있으나 범용 typing/filter UX는 부분 또는 미구현이다.
- 예상 영역: QuizQuestionFactory, QuizSession mode/filter DTO, UI/API, tests.
- 선행 dependency: Phase 0 candidate/provenance 완료.
- 난이도: Medium / 추천 모델: Sol Medium.

#### Ticket 1D-2: 퀴즈 이력 UX
- 목표: session, 문제 원본, 정오답, 재시도 맥락을 사용자가 탐색할 수 있게 한다.
- 현재 상태: QuizSession/QuizAttempt와 이력 endpoint는 있으나 전용 상세 UX가 제한적이다.
- 예상 영역: quiz history controller/template/API DTO, provenance-aware query, tests.
- 선행 dependency: Phase 0 Ticket 2 완료.
- 난이도: Medium / 추천 모델: Sol Medium.

### Phase 1E — 알림과 개인화

#### Ticket 1E-1: 복습 알림 delivery
- 목표: 저장된 reminder preference를 실제 scheduler/push/email delivery로 연결한다.
- 현재 상태: enabled/time preference 저장만 구현되어 있다.
- 예상 영역: scheduler, delivery provider, timezone/opt-out, delivery log, tests.
- 선행 dependency: 운영 계정/backup 정책.
- 난이도: Large / 추천 모델: Sol High.

#### Ticket 1E-2: 활동·개인화 통계
- 목표: 활동 캘린더, 카드별 이력, 시간대별 성과를 단계적으로 제공한다.
- 현재 상태: 일/주간 기록, streak, accuracy, weekly activity는 구현됐고 월간/retention/시간대 분석은 제한적이다.
- 예상 영역: analytics query/read model, index 검토, statistics UI, tests.
- 선행 dependency: 1C 복습 제어 및 충분한 이벤트 데이터.
- 난이도: Medium / 추천 모델: Sol Medium.

## Phase 1 후보 비교

| 후보 | 사용자 가치 | 선행 dependency | 난이도 | DB 영향 | 기존 재사용 | 경쟁력 | V1 필요도 |
|---|---|---|:---:|---|---|---|---|
| 공개 콘텐츠 승인·품질 게이트 | 실제 학습 가능한 JLPT 체급과 신뢰 확보 | 없음 | Large | 품질 상태·검수 이력 보강 가능 | import/review 구조 재사용 | 대규모 콘텐츠를 공개 품질로 전환 | **필수** |
| 단어·예문 음성 | 발음·청해와 기억 단서 강화 | 공개 콘텐츠 품질 | Medium | audio 메타데이터/캐시 선택 | `audio_file_name`, content DTO 재사용 | Anki보다 즉시성, Migii와 경쟁 | 높음 |
| 활용형/문법 예문 보강 | 문맥 이해와 생산 능력 강화 | 공개 콘텐츠 품질 | Large | conjugation 관계 모델/import | Word/Grammar/Example 재사용 | 문법 깊이 차별화 | 중간 |
| 사용자 콘텐츠 오류 신고 | 공개 품질을 지속적으로 개선 | 품질 상태/운영 정책 | Medium | 신고·검수 이력 추가 | 관리자 review 흐름 재사용 | 신뢰도와 운영 피드백 루프 | 높음 |
| 검색 결과 바로 저장/학습 | 발견에서 학습까지 전환 마찰 감소 | 공개 콘텐츠 품질 | Small | 기존 bookmark/collection 연결 | Dictionary/search route 재사용 | 차분한 발견→학습 루프 강화 | 높음 |
| Archive/Suspend UX | 원치 않는 카드의 복습 노출 제어 | Phase 0 due semantics | Medium | 기존 learningState 사용 | LearningService/API 재사용 | Anki식 통제력을 단순하게 제공 | 높음 |
| review controls | skip/relearn/retry 의미를 명확히 함 | Archive/Suspend UX | Medium | 상태 전이·기록 보강 가능 | Today/Study 흐름 재사용 | recall 품질과 지속성 향상 | 높음 |
| Known/Skip | 이미 아는 항목의 초기 학습 부담 감소 | Archive/Suspend UX | Medium | 상태/이벤트 정책 확장 | SUSPENDED·학습 기록 재사용 | 성인 학습자의 조절감 | 중간 |
| 실제 reminder delivery | 재방문을 안정적으로 유도 | 계정/운영·시간대 정책 | Large | delivery log/opt-out 추가 | reminder preference 재사용 | 습관 형성 보완, 게임화 없이 유지 | V1 이후 |
| 퀴즈 타이핑 | 회상 난도를 높이고 생산 연습 제공 | Phase 0 candidate/provenance | Medium | QuizSession mode/attempt 확장 가능 | QuizQuestionFactory 재사용 | Anki·Migii와의 recall 경쟁력 | 중간 |
| 퀴즈 상태/오답 필터 | 취약 항목 집중 학습 | candidate scope/provenance | Medium | session filter/snapshot 메타데이터 | LearningProgress·QuizSession 재사용 | recall 중심 루프 강화 | 중간 |
| 퀴즈 이력 UX | 정오답 원인과 재시도 맥락 확인 | QuizAttempt provenance | Medium | 기존 attempt 조회 확장 | history/analytics endpoint 재사용 | Anki식 기록성과 제품 완성도 | 중간 |
| FSRS | 장기 retention 최적화 가능성 | 충분한 review history·custom SRS 검증 | Large | 스케줄 필드/마이그레이션 영향 큼 | Due semantics만 부분 재사용 | Anki 대비 장기 경쟁력 | V1 이후 |
| Kanji foundation | 단어 학습을 한자 이해로 확장 | 공개 콘텐츠 품질·관계 설계 | Large | `KanjiEntry`·읽기·관계 모델 신설 | Word/Example 연결 재사용 | JLPT 심화 차별화 | V1 이후 |

### Phase 2 — 일본어 학습 깊이
- 유의어·반의어·관련어
- 문법 confirmation/context 품질 강화
- 독해·청해의 작은 파일럿
- 개인화 추천을 위한 데이터 모델 확장

### Phase 3 — 한자/Visual Memory
먼저 독립 `KanjiEntry` 계층을 설계한 뒤 시작한다. 한자 상세 + 음독/훈독 + 단어 연결 → 구성 요소/유사 한자 → 획순·쓰기·mnemonic 순서가 안전하다.

### Phase 4 — Haru Motivation Layer
Haru를 일반 화면 장식으로 되돌리지 않는다. `실제 학습 EXP → Stage → /haru에서 성장 확인 → 의미 있는 milestone 반응`을 선택적으로 연결한다. 코인·방 꾸미기·시즌·리더보드는 초기 핵심이 아니다.

### Phase 5 — JLPT 시험 확장
`레벨 진도 + 개인 로드맵 → 섹션 연습 → 독해/청해 → 정답 해설 → 실전 모의고사` 순서로 확장한다. 모의고사는 콘텐츠 검수와 운영 능력이 확보된 뒤 진행한다.

## 특히 복제하지 말고 '원리만 가져올 것'
1. iroiro의 대규모 수작업 mnemonic 일러스트 물량: 1인 개발에서 콘텐츠 병목이 매우 큼.
2. renshuu의 방대한 10,000+ 사용자 레슨/커뮤니티: 네트워크 효과가 필요한 영역.
3. Migii의 대량 실전 모의고사: 문항 제작/검수 비용이 큼.
4. Duolingo식 대규모 리그 운영: 유저 풀이 충분해진 뒤.
5. Anki 수준의 무한 커스터마이징: 초기에 넣으면 UX 복잡도가 폭발함.

## 현재 가장 중요한 제품 정의

**“대규모 JLPT 단어·문법 콘텐츠 + 회상 중심의 오늘 학습/SRS + 차분한 일본어 학습 UX + 장기 동기부여로 작동하는 Haru.”**

Haru는 홈/학습 화면을 점유하는 장식이나 게임 UI가 아니라 **학습 결과가 쌓였음을 보여 주는 Motivation Layer**다.

핵심 루프:

`검색/발견 → 신규 학습 → Prompt → Reveal → Recall → SRS 복습 → 기록/약점 → 장기 성장(Haru) → 다음날 재방문`

제품 우선순위는 항상 다음 순서를 따른다.

`콘텐츠 신뢰성 > 회상/복습 품질 > 학습 흐름 UX > 통계/개인화 > Haru 보상 > 부가 게임화`

## 조사 출처
- iroiro JLPT App Store (기능/버전 기록): https://apps.apple.com/jp/app/iroiro-jlpt-japanese-vocab/id6752556844
- iroiro JLPT Google Play: https://play.google.com/store/apps/details?id=kr.iro.iroirojlpt
- Anki Manual / FSRS: https://docs.ankiweb.net/deck-options
- Anki Statistics: https://docs.ankiweb.net/stats.html
- Migii JLPT Google Play: https://play.google.com/store/apps/details?id=com.eup.mytest
- Migii JLPT App Store: https://apps.apple.com/jp/app/id1463267540
- Duolingo Japanese characters: https://blog.duolingo.com/learning-to-read-japanese-characters/
- Duolingo gamification overview: https://play.google.com/store/apps/editorial?id=mc_apps_editorial_user_education_duolingo_fcp
- renshuu Google Play: https://play.google.com/store/apps/details?id=com.renshuu.renshuu_org
- renshuu official site: https://www.renshuu.org/
- Bunpro Pricing/Features: https://bunpro.jp/pricing
- Bunpro Decks: https://bunpro.jp/decks/bunpro

## 조사 메모
경쟁사 기능은 공식 스토어 설명, 공식 매뉴얼/지원 문서, 공식 업데이트 기록을 우선 사용했습니다.
빠르게 변하는 앱이므로 큰 릴리스가 나온 뒤에는 이 파일의 체크를 다시 검증하는 것을 권장합니다.
