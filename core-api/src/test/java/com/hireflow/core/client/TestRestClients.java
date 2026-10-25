package com.hireflow.core.client;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.yaml.MappingJackson2YamlHttpMessageConverter;
import org.springframework.web.client.RestClient;

final class TestRestClients {

    private TestRestClients() {
    }

    static WireMockServer wireMock() {
        // The JDK HTTP client upgrades to HTTP/2, and WireMock then resets the stream.
        // ParserClient and MatcherClient map that reset as a connection failure.
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort().http2PlainDisabled(true));
        server.start();
        return server;
    }

    static RestClient.Builder snakeCase() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        return RestClient.builder().messageConverters(converters -> {
            // wiremock-standalone puts Jackson's YAML factory on the test classpath.
            // RestClient would otherwise serialize as application/yaml. Production sends JSON.
            converters.removeIf(MappingJackson2YamlHttpMessageConverter.class::isInstance);
            for (HttpMessageConverter<?> converter : converters) {
                if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                    jackson.setObjectMapper(mapper);
                }
            }
        });
    }
}
