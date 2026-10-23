package com.hireflow.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.format.jackson.JacksonJsonFormatMapper;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

@Configuration
public class ClientConfig {

    @Bean
    RestClientCustomizer restClientTimeouts() {
        return builder -> {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
            factory.setReadTimeout(Duration.ofSeconds(120));
            builder.requestFactory(factory);
        };
    }

    @Bean
    HibernatePropertiesCustomizer hibernateJson(ObjectMapper objectMapper) {
        return properties -> properties.put(
                AvailableSettings.JSON_FORMAT_MAPPER,
                new JacksonJsonFormatMapper(objectMapper));
    }
}
