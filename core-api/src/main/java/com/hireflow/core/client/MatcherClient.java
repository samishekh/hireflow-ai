package com.hireflow.core.client;

import com.hireflow.core.config.HireflowProperties;
import com.hireflow.core.service.PermanentException;
import com.hireflow.core.service.RetryableException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MatcherClient {

    private final RestClient restClient;

    public MatcherClient(RestClient.Builder builder, HireflowProperties properties) {
        this.restClient = builder.baseUrl(ClientSupport.trimSlash(properties.matcherBaseUrl())).build();
    }

    public ScoreResponse score(ScoreRequest request) {
        try {
            ScoreResponse response = restClient.post()
                    .uri("/score")
                    .body(request)
                    .retrieve()
                    .body(ScoreResponse.class);
            if (response == null || response.formulaVersion() == null || response.model() == null) {
                throw new PermanentException("Job matcher returned an empty score");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw ClientSupport.map("job matcher", exception);
        } catch (ResourceAccessException exception) {
            throw new RetryableException("Could not reach job matcher", exception);
        }
    }
}
