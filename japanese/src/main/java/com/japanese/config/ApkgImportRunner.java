package com.japanese.config;

import com.japanese.content.importer.ApkgVocabularyImporter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Profile("import-sample")
public class ApkgImportRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApkgImportRunner.class);

    private final ApkgVocabularyImporter importer;

    @Value("${japanese.import.apkg-path:}")
    private String apkgPath;

    public ApkgImportRunner(ApkgVocabularyImporter importer) {
        this.importer = importer;
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("APKG import runner source: {}", apkgPath == null || apkgPath.isBlank() ? "(not configured)" : apkgPath);
        if (apkgPath == null || apkgPath.isBlank() || !Files.isRegularFile(Path.of(apkgPath))) {
            log.warn("APKG source is unavailable; skipping import-sample run");
            return;
        }
        importer.importVocabularySample();
    }
}
