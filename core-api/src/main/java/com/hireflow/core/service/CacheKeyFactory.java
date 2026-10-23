package com.hireflow.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.WorkExperience;
import org.springframework.stereotype.Component;

@Component
public class CacheKeyFactory {

    private final ObjectMapper objectMapper;

    public CacheKeyFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String cacheKey(String descriptionHash, BlindCandidate blind) {
        try {
            String canonical = objectMapper.writeValueAsString(canonicalBlind(blind));
            return Digests.sha256(descriptionHash + "\n" + canonical);
        } catch (Exception exception) {
            throw new PermanentException("Could not build the score cache key", exception);
        }
    }

    private ObjectNode canonicalBlind(BlindCandidate blind) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode education = root.putArray("education");
        for (Education item : blind.education()) {
            ObjectNode node = education.addObject();
            node.put("degree", item.degree());
            node.put("duration", item.duration());
            node.put("field_of_study", item.fieldOfStudy());
            node.put("institution", item.institution());
        }
        ArrayNode skills = root.putArray("skills");
        blind.skills().forEach(skills::add);
        ArrayNode workHistory = root.putArray("work_history");
        for (WorkExperience item : blind.workHistory()) {
            ObjectNode node = workHistory.addObject();
            node.put("company", item.company());
            node.put("description", item.description());
            node.put("duration", item.duration());
            node.put("role", item.role());
        }
        root.put("years_of_experience", blind.yearsOfExperience());
        return root;
    }
}
