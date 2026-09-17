package com.japanese.content.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.japanese.content.entity.NormalizedCandidateType;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * JLPT-MAX Ticket 4E-0: {@link ProductionContentSlugPolicy} is a pure, source-independent slug
 * generator - no database or Spring context needed.
 */
class ProductionContentSlugPolicyTest {

    private static final Pattern SLUG_PATTERN =
            Pattern.compile("^(word|grammar)-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    @Test
    void vocabularyCandidatesGetAWordPrefixedSlug() {
        ProductionContentSlugPolicy policy =
                new ProductionContentSlugPolicy(() -> UUID.fromString("550e8400-e29b-41d4-a716-446655440000"));
        assertThat(policy.generateSlug(NormalizedCandidateType.VOCABULARY))
                .isEqualTo("word-550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void grammarCandidatesGetAGrammarPrefixedSlug() {
        ProductionContentSlugPolicy policy =
                new ProductionContentSlugPolicy(() -> UUID.fromString("550E8400-E29B-41D4-A716-446655440000"));
        assertThat(policy.generateSlug(NormalizedCandidateType.GRAMMAR))
                .isEqualTo("grammar-550e8400-e29b-41d4-a716-446655440000");
    }

    @Test
    void generatedSlugsMatchTheDocumentedFormatAndFitTheProductionColumn() {
        ProductionContentSlugPolicy policy = new ProductionContentSlugPolicy();
        for (NormalizedCandidateType type : NormalizedCandidateType.values()) {
            String slug = policy.generateSlug(type);
            assertThat(slug).matches(SLUG_PATTERN);
            assertThat(slug.length()).isLessThanOrEqualTo(120);
        }
    }

    @Test
    void differentCallsProduceDifferentSlugs() {
        ProductionContentSlugPolicy policy = new ProductionContentSlugPolicy();
        String first = policy.generateSlug(NormalizedCandidateType.VOCABULARY);
        String second = policy.generateSlug(NormalizedCandidateType.VOCABULARY);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void bothCandidateTypesAreSupportedToday() {
        ProductionContentSlugPolicy policy = new ProductionContentSlugPolicy();
        assertThat(policy.supports(NormalizedCandidateType.VOCABULARY)).isTrue();
        assertThat(policy.supports(NormalizedCandidateType.GRAMMAR)).isTrue();
    }

    @Test
    void publicApiTakesNoSourceOrCandidateIdentityParameter() throws NoSuchMethodException {
        // getMethod(...) itself fails if this exact single-parameter overload did not exist - this
        // pins the signature this policy's javadoc promises.
        Method generateSlug =
                ProductionContentSlugPolicy.class.getMethod("generateSlug", NormalizedCandidateType.class);
        Method supports = ProductionContentSlugPolicy.class.getMethod("supports", NormalizedCandidateType.class);
        assertThat(generateSlug.getParameterCount()).isEqualTo(1);
        assertThat(supports.getParameterCount()).isEqualTo(1);

        // getMethod(...) alone cannot prove a second, source/candidate-identity-accepting overload
        // was not added alongside it - enumerate every public generateSlug method to close that gap.
        List<Method> generateSlugOverloads = Arrays.stream(ProductionContentSlugPolicy.class.getMethods())
                .filter(m -> m.getName().equals("generateSlug"))
                .toList();
        assertThat(generateSlugOverloads).hasSize(1);
        assertThat(generateSlugOverloads.get(0).getParameterTypes()).containsExactly(NormalizedCandidateType.class);
    }
}
