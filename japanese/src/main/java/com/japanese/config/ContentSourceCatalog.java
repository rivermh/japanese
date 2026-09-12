package com.japanese.config;

import com.japanese.content.entity.ContentSource;
import com.japanese.content.repository.ContentSourceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile({"sample", "import-sample"})
public class ContentSourceCatalog implements CommandLineRunner {

    private static final String JLPT_SOURCE_REF = "JLPT-MAX-Deck-2.1.1.apkg";
    private static final String LICENSE_URL = "https://github.com/truthyblue/jlpt-max-deck/blob/main/docs/privacy-and-licensing.md";

    private final ContentSourceRepository contentSourceRepository;

    public ContentSourceCatalog(ContentSourceRepository contentSourceRepository) {
        this.contentSourceRepository = contentSourceRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        contentSourceRepository.findBySourceRef(JLPT_SOURCE_REF)
                .orElseGet(() -> contentSourceRepository.save(new ContentSource(
                        JLPT_SOURCE_REF,
                        "JLPT MAX Deck",
                        "2.1.1",
                        "원자료별 라이선스 확인 필요",
                        LICENSE_URL,
                        "Kanjium, AnimCJK, AivisSpeech 등 upstream 고지 참조",
                        "APKG 데이터의 재배포 권한은 별도 확인이 필요함")));
        contentSourceRepository.findBySourceRef("sample")
                .orElseGet(() -> contentSourceRepository.save(new ContentSource(
                        "sample",
                        "Japanese sample content",
                        "initial",
                        "프로젝트 샘플 데이터",
                        null,
                        "Japanese project",
                        "개발 확인용 데이터")));
    }
}
