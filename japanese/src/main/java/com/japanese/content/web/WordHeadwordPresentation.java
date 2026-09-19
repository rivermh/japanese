package com.japanese.content.web;

import com.japanese.content.entity.ContentType;
import org.springframework.stereotype.Component;

/** Presentation rules for a vocabulary headword using only stored reading data. */
@Component("wordHeadword")
public class WordHeadwordPresentation {
    public boolean shouldRenderRuby(ContentType type, String surface, String reading) {
        return type == ContentType.WORD && shouldRenderRuby(surface, reading);
    }

    public boolean shouldRenderRuby(String surface, String reading) {
        return surface != null && !surface.isBlank()
                && reading != null && !reading.isBlank()
                && !surface.equals(reading);
    }
}
