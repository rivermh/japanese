# Japanese

JLPT N5~N1 단어·문법·예문을 찾고 학습하고 SRS로 복습하는 일본어 학습/사전 웹 서비스. 검색, 오늘의 학습, 퀴즈, 약점 노트, 주간 리포트, 성장 캐릭터(Haru)를 제공하며 향후 IT·호텔·비즈니스 등 분야별 일본어와 Android 클라이언트로 확장할 수 있게 설계되어 있다.

## 주요 기능

- JLPT 단어/문법 검색과 상세 (한자, 히라가나, 가타카나, 한국어 의미 검색 지원)
- 오늘의 학습(SRS 기반 복습 + 신규 학습), 자유 학습, 빠른 Quiz
- 학습 큐 / 북마크 / 나만의 컬렉션
- 약점 노트, 주간 학습 리포트, JLPT 진도맵
- 계정(회원가입, 이메일 인증, 비밀번호 재설정), 성장 캐릭터 Haru
- 관리자 콘텐츠 검수/품질 감사 콘솔
- JLPT-MAX 원본을 production 콘텐츠로 승격하기 전 단계인 private 콘텐츠 정규화/후보 검토 파이프라인

## Stack

- Java 17, Spring Boot, Gradle
- Spring Data JPA, MySQL, Flyway
- Thymeleaf, HTML/CSS/JavaScript
- 테스트: JUnit, Spring Boot Test, H2

## 실행 방법

```powershell
cd japanese
copy secrets.example.yml secrets.yml   # DB_URL/DB_USERNAME/DB_PASSWORD 등 로컬 값으로 수정
.\gradlew.bat bootRun --args="--spring.profiles.active=sample"
```

기본 주소는 `http://localhost:8080`이며 기본 프로필은 `sample`이다.

## 테스트

```powershell
cd japanese
.\gradlew.bat test
```

실제 APKG 파일 경로가 있을 때만 실행되는 opt-in 통합 테스트는 파일이 없으면 자동으로 skip된다.

## 디렉터리 구조

```
japanese/                    Spring Boot 애플리케이션 (소스, 테스트, migration)
  src/main/java/com/japanese/account    계정/인증
  src/main/java/com/japanese/content    콘텐츠, import, 정규화/후보 파이프라인
  src/main/java/com/japanese/learning   학습, SRS, 퀴즈, 캐릭터
  src/main/resources/db/migration       Flyway migration (MySQL/H2)
docs/                        프로젝트 문서
docs/development/            구현 기록, 개발 가이드라인
design-prototype/            초기 디자인 프로토타입 (비교 기준으로 보존)
```

## 문서

- [docs/current-features.md](docs/current-features.md) — 현재 구현된 기능 현황
- [docs/data-model.md](docs/data-model.md) — 콘텐츠 데이터 모델
- [DESIGN.md](DESIGN.md) — 디자인 가이드
- [docs/development/IMPLEMENTATION_LOG.md](docs/development/IMPLEMENTATION_LOG.md) — 구현 이력
- [docs/development/PROJECT_GUIDELINES.md](docs/development/PROJECT_GUIDELINES.md) — 프로젝트 방향과 개발 가이드라인
- [japanese/docs/operations/database-migrations.md](japanese/docs/operations/database-migrations.md) — DB migration 운영 가이드
