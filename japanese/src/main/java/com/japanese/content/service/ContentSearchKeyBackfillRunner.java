package com.japanese.content.service;

import com.japanese.content.repository.ContentItemRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(100)
public class ContentSearchKeyBackfillRunner implements ApplicationRunner {

    private static final int BATCH_SIZE = 250;

    private final ContentItemRepository contentItemRepository;

    public ContentSearchKeyBackfillRunner(ContentItemRepository contentItemRepository) {
        this.contentItemRepository = contentItemRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        while (true) {
            var items = contentItemRepository.findNeedingSearchKeyBackfill(PageRequest.of(0, BATCH_SIZE));
            if (items.isEmpty()) {
                return;
            }
            items.forEach(item -> item.refreshSearchKeys());
            contentItemRepository.flush();
        }
    }
}
