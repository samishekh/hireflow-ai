package com.hireflow.core.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

final class CompareRankingsTransformer implements ResponseDefinitionTransformerV2 {

    static final String NAME = "compare-rankings";

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean applyGlobally() {
        return false;
    }

    @Override
    public ResponseDefinition transform(ServeEvent serveEvent) {
        try {
            JsonNode request = JSON.readTree(serveEvent.getRequest().getBody());
            ArrayNode rankings = JSON.createArrayNode();
            int rank = 1;
            for (JsonNode candidate : request.path("candidates")) {
                String id = candidate.path("candidate_id").asText();
                if (id.isBlank()) {
                    continue;
                }
                rankings.addObject()
                        .put("candidate_id", id)
                        .put("rank", rank)
                        .put("reason", "relative fit");
                rank++;
            }
            if (rankings.isEmpty()) {
                throw new IllegalStateException("compare request had no candidate ids");
            }
            ObjectNode response = JSON.createObjectNode();
            response.set("rankings", rankings);
            response.put("model", "mock");
            return aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(JSON.writeValueAsString(response))
                    .build();
        } catch (Exception exception) {
            return aResponse()
                    .withStatus(500)
                    .withBody(exception.getMessage() == null ? "compare transform failed" : exception.getMessage())
                    .build();
        }
    }
}
