package com.japanese.content.service;

import com.japanese.content.entity.NormalizedCandidateType;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/**
 * JLPT-MAX Ticket 4E-0: the ratified production {@code ContentItem.slug}/global-identity policy - a
 * source-independent opaque identifier, never derived from any private-candidate or source-native
 * value ({@code sourceRef}/{@code sourceNoteId}/{@code EntryID}/{@code UnitID}/{@code expression}/
 * {@code pattern}/APKG deck path/candidate id). Every slug this policy produces is
 * {@code <prefix>-<lowercase UUID>} - well under {@code ContentItem.slug}'s 120-char column limit -
 * so it can never collide with the legacy {@code ApkgVocabularyImporter} {@code "jlpt-max-"}/
 * {@code "jlpt-max-grammar-"} slug prefixes, which this ticket does not touch.
 *
 * <p>This class only decides what a slug looks like; it never persists anything and never checks
 * {@code ContentItem.slug} uniqueness itself - that DB-level unique constraint remains the final
 * integrity guard for a future promotion transaction (Ticket 4E-1), which must roll back its whole
 * transaction on a unique violation rather than retry with a different slug from inside this class.
 *
 * <p>{@link #generateSlug} is only ever meant to be called from a promotion write transaction - never
 * from read-only readiness computation, which must never mint a new identity on every GET (see
 * {@code NormalizedCandidatePromotionReadinessService}, which only ever calls {@link #supports}).
 */
@Service
public class ProductionContentSlugPolicy {

    private final Supplier<UUID> uuidSupplier;

    public ProductionContentSlugPolicy() {
        this(UUID::randomUUID);
    }

    ProductionContentSlugPolicy(Supplier<UUID> uuidSupplier) {
        this.uuidSupplier = uuidSupplier;
    }

    /** Whether this policy can produce a slug for the given candidate type today. */
    public boolean supports(NormalizedCandidateType candidateType) {
        return candidateType == NormalizedCandidateType.VOCABULARY || candidateType == NormalizedCandidateType.GRAMMAR;
    }

    /** Generates a fresh, source-independent slug for the given candidate type. */
    public String generateSlug(NormalizedCandidateType candidateType) {
        if (!supports(candidateType)) {
            throw new IllegalArgumentException("No production slug policy for candidate type " + candidateType);
        }
        String prefix = candidateType == NormalizedCandidateType.VOCABULARY ? "word" : "grammar";
        return prefix + "-" + uuidSupplier.get().toString().toLowerCase(Locale.ROOT);
    }
}
