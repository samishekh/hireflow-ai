package com.hireflow.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.WorkExperience;
import java.util.List;
import org.junit.jupiter.api.Test;

class CacheKeyFactoryTest {

    private static final String DESCRIPTION_HASH = "a".repeat(64);

    private final CacheKeyFactory cacheKeys = new CacheKeyFactory(new ObjectMapper());

    @Test
    void sameBlindProfileAndDescriptionHashProduceTheSameKey() {
        BlindCandidate first = profile(List.of("Java", "SQL"));
        BlindCandidate second = profile(List.of("Java", "SQL"));

        String key = cacheKeys.cacheKey(DESCRIPTION_HASH, first);

        assertThat(key).isEqualTo(cacheKeys.cacheKey(DESCRIPTION_HASH, second));
        assertThat(cacheKeys.cacheKey("b".repeat(64), first)).isNotEqualTo(key);
    }

    @Test
    void changingASkillChangesTheKey() {
        String original = cacheKeys.cacheKey(DESCRIPTION_HASH, profile(List.of("Java", "SQL")));
        String changed = cacheKeys.cacheKey(DESCRIPTION_HASH, profile(List.of("Java", "Go")));

        assertThat(changed).isNotEqualTo(original);
    }

    private static BlindCandidate profile(List<String> skills) {
        return new BlindCandidate(
                skills,
                6.0,
                List.of(new WorkExperience("Northwind", "Engineer", "2019-2024", "Built APIs")),
                List.of(new Education("State University", "BS", "Computer Science", "2015-2019")));
    }
}
