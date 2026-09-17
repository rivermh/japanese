# Project Guidelines

## Project

이 프로젝트는 일본어 학습 및 사전 서비스이다. 초기 목표는 JLPT N5~N1의 단어, 문법, 예문을 체계적으로 학습할 수 있는 서비스를 만드는 것이다. 이후 IT, 호텔, 비즈니스, 경어, 취업, 일상생활 등 분야별 일본어를 추가하여 종합 일본어 학습/사전 서비스로 확장한다.

Entity, DB, 검색, 카테고리 구조를 JLPT에만 종속시키지 않는다. JLPT는 최초 콘텐츠 영역일 뿐 시스템 전체의 고정된 경계가 아니다.

## Stack & Architecture

* Java 17
* Spring Boot / Gradle
* Spring Data JPA / MySQL
* Thymeleaf / HTML / CSS / JavaScript

기본 구조는 `Controller → Service → Repository → Entity`를 따른다. 필요시 DTO, Exception, Config 등을 추가하되 불필요한 추상화나 과도하게 복잡한 아키텍처를 도입하지 않는다. 기본 구조보다 더 적합한 구조가 있으면 적극적으로 채택한다.

웹 서비스를 먼저 완성한 후 Android 앱으로 확장한다. 핵심 비즈니스 로직을 View나 Controller에 종속시키지 않고 향후 REST API와 Android에서도 재사용 가능하게 설계한다.

## Core

초기 핵심은 JLPT N5~N1 단어, 문법, 예문, 검색/분류, 학습/복습, 사용자 학습 기록이다.

홈에는 학습으로 EXP를 얻어 일정 구간마다 성장하는 캐릭터를 둔다. 캐릭터는 학습 지속을 위한 보조 요소이며 게임보다 일본어 학습 경험과 콘텐츠 품질을 우선한다.

## Design

전체 UI는 현대적이고 깔끔하며 직관적인 학습 서비스로 디자인한다. 모바일 앱으로 확장할 것을 고려하여 반응형으로 구현한다.

불필요한 공백과 과도한 여백을 만들지 않는다. 화면을 의미 없이 크게 벌리는 padding, margin, 빈 영역, 과도하게 큰 카드와 섹션을 피한다. 정보 밀도와 가독성의 균형을 유지하며 한 화면의 공간을 효율적으로 사용한다.

일관된 typography, spacing, button, card, input, color system을 유지한다. 기능마다 제각각인 UI를 만들지 않고 공통 컴포넌트와 스타일을 재사용한다. 캐릭터가 존재하더라도 지나치게 유아적이거나 게임 UI처럼 만들지 않는다.

## Reference Data

프로젝트 루트의 `JLPT-MAX-Deck-2.1.1` 자료는 JLPT 콘텐츠 구축 시 참고 자료로 활용할 수 있다.

구현 전에 해당 자료의 파일 형식, 내부 구조, 데이터 필드 및 라이선스/재사용 조건을 먼저 확인한다. 원본을 무조건 복사하거나 기존 구조에 맞춰 시스템을 설계하지 말고, 프로젝트의 데이터 모델에 적합한 방식으로 참고한다.

## Development Rules

구현 전 관련 코드와 데이터 흐름을 먼저 확인한다. 기존 기능, API, DB 구조와 테스트를 이유 없이 삭제하거나 변경하지 않는다.

임시 하드코딩, UI만 존재하는 가짜 기능, 동작하지 않는 placeholder를 완료된 기능으로 취급하지 않는다. 기능은 실제 데이터 흐름을 통해 처음부터 끝까지 동작해야 한다.

확장성을 고려하되 아직 필요하지 않은 기능을 과도하게 미리 구현하지 않는다. 단순하고 명확하며 유지보수 가능한 코드를 우선한다.

## Code Style

새 코드는 기존 코드베이스의 naming, package 구조, method/class 분리 방식, exception/null 처리, test 스타일, comment/Javadoc 밀도를 먼저 확인하고 자연스럽게 맞춘다.

자명한 코드를 설명하는 장황한 주석, 기존 프로젝트 스타일에 없는 과도한 Javadoc, 불필요한 interface/factory/wrapper/helper 계층을 만들지 않는다.

기존 코드베이스와 일관된 production-quality code를 우선한다. 단, 버그나 테스트 실패, provenance, 미확정 사항을 숨기거나 사실과 다르게 기록해서는 안 된다.
