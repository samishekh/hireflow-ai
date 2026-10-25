package com.hireflow.core.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;

final class HoldParseTransformer implements ResponseDefinitionTransformerV2 {

    static final String NAME = "hold-parse";

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
        ParseGate.awaitOpen();
        return aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(ParseGate.body())
                .build();
    }
}
