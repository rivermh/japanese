# JLPT-MAX-Deck-2.1.1 분석

분석 대상은 프로젝트 루트의 `JLPT-MAX-Deck-2.1.1.apkg`이다. 원본 APKG의 `collection.anki21`만 읽어 어휘와 문법을 프로젝트 모델로 변환했으며, ZIP 안의 미디어 파일은 추출하거나 애플리케이션 데이터로 복사하지 않는다.

## 파일과 내부 구조

APKG는 ZIP 아카이브다. 다음 항목이 확인됐다.

| 항목 | 확인 결과 |
| --- | --- |
| `collection.anki21` | 146,702,336 bytes인 SQLite 컬렉션. 실제 최신 노트와 카드가 들어 있다. |
| `collection.anki2` | 151,552 bytes인 구형 호환 컬렉션. 내용은 `This file requires a newer version of Anki.`라는 안내 노트 1개뿐이다. |
| `media` | JSON 매니페스트 25,511개 |
| 숫자 파일 | 미디어 본문 25,511개 |

전체 ZIP 항목은 25,514개, 압축 해제 기준 약 1.28GB이다. 미디어는 MP3 25,508개와 WOFF2 글꼴 3개로 확인됐다. 최신 컬렉션에는 Anki의 `col`, `notes`, `cards`, `notetypes`, `fields`, `templates`, `decks`, `tags` 등의 테이블이 있다. 노트 필드는 ASCII Unit Separator인 `\\x1f`로 구분된다.

## 노트와 데이터 규모

`collection.anki21`에서 노트 유형별로 다음을 확인했다.

| 노트 유형 | 노트 수 | 주요 데이터 |
| --- | ---: | --- |
| `JLPT MAX덱 어휘` | 9,160 (활성 9,159) | EntryID, Word, Reading, Meaning, PartOfSpeech, JLPT, 악센트 HTML, 활용·관련어·사용 정보, 예문 렌더링, 오디오 |
| `JLPT MAX덱 어휘문제` | 7,876 | JLPT, 문제 유형 10종, 일본어 문장, 선택지, 정답·해설, 루비 |
| `JLPT MAX덱 문법` | 3,605 | Level, UnitID, FrontHTML, BackHTML, 기본·문법형·문장 배열·빈칸 플래그 |
| `JLPT MAX덱 참조표` | 9 | 제목, JLPT, 표 종류, 표 HTML |

총 20,650개 노트와 38,967개 카드가 있다. 레벨은 모든 핵심 유형에서 N5, N4, N3, N2, N1을 확인했다. 어휘 활성 노트는 N5 779, N4 878, N3 1,719, N2 2,632, N1 3,151개이며, 문법은 N5 340, N4 247, N3 759, N2 899, N1 1,360개다. 어휘 문제는 N5 832, N4 1,661, N3 1,621, N2 1,742, N1 2,020개다.

어휘에는 예문이 5개의 `ExampleN*` 열로 비어 있는 경우가 많고, 실제 표시용 HTML이 `ExamplesRendered`에 들어 있다. 이 값은 `ruby`, CSS 클래스, 오디오 파일명, 한국어 번역을 포함한다. 그러므로 `ExamplesRendered`를 그대로 서비스 HTML로 저장하지 않고, HTML 파서와 허용 태그 정책을 거쳐 `Example` 레코드로 변환해야 한다. 현재 분석에서 어휘 9,159개 모두 예문 렌더링을 갖고 있으며, 공식 사이트도 이를 검토된 예문 10,997개로 설명한다.

어휘 표제어는 9,146개가 유일하다. 같은 표제어가 여러 의미·읽기를 갖는 사례가 있어 Word를 표제어 문자열만으로 유일하게 만들면 안 된다. 활성 어휘의 `(Word, Reading)` 조합은 모두 유일했지만, import 시 EntryID와 원자료 버전을 보존하고 의미 단위는 별도 레코드로 분리한다. 폐기 플래그가 있는 1개 노트는 기본 import에서 제외하고 감사 로그에 남기는 것이 적절하다.

## 라이선스와 재사용 경계

APKG 내부에는 공식 저장소·지원 페이지 링크와 Kanjium의 CC BY-SA 4.0, AnimCJK의 Arphic Public License에 대한 표기가 들어 있다. 또한 MP3는 AivisSpeech 1.2.0과 `まい` 음성 모델로 생성됐다는 정보가 확인된다.

공식 프로젝트의 [개인정보·저작권·라이선스 문서](https://github.com/truthyblue/jlpt-max-deck/blob/main/docs/privacy-and-licensing.md)와 [NOTICE](https://github.com/truthyblue/jlpt-max-deck/blob/main/NOTICE)는 코드 라이선스와 덱 데이터의 조건을 분리한다. NOTICE에 따르면 JMdict·KANJIDIC2 파생 부분은 EDRDG 조건과 CC BY-SA 4.0 범위를 따르고, 프로젝트 작성 예문·한국어 번역·문제·생성 음성은 별도 조건을 가진다. 공식 APKG와 그 데이터 컬렉션은 개인 학습용 허가와 별개로 재포장·수정·미러링·재배포 권한을 자동으로 부여하지 않는다.

따라서 import는 원본 APKG 자체를 배포 산출물로 복사하는 방식이 아니라, 출처와 버전을 기록한 구조화 데이터 변환으로 수행했다. 현재 DB에는 활성 어휘 9,159건과 문법 3,605건이 검토 대기 상태로 저장되어 있으며, 폐기 태그가 있는 어휘 1건은 제외했다. 문제 카드 7,876건과 참조표 9건, 총 7,885건은 `ImportedSourceRecord`에 note type·원본 note ID·필드 이름·필드 payload를 보존해 두었고, 다음 단계에서 문제 풀이·참조표 모델로 변환할 수 있다. 특히 렌더링 HTML과 MP3 전체를 그대로 서비스에 넣는 방식은 채택하지 않는다.
