package com.hireflow.core.client;

import com.hireflow.core.config.HireflowProperties;
import com.hireflow.core.service.PermanentException;
import com.hireflow.core.service.RetryableException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class ParserClient {

    private final RestClient restClient;

    public ParserClient(RestClient.Builder builder, HireflowProperties properties) {
        this.restClient = builder.baseUrl(ClientSupport.trimSlash(properties.parserBaseUrl())).build();
    }

    public ParseResumeResponse parse(String filename, byte[] pdf) {
        ByteArrayResource resource = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.APPLICATION_PDF);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new HttpEntity<>(resource, partHeaders));

        try {
            ParseResumeResponse response = restClient.post()
                    .uri("/parse-resume")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(ParseResumeResponse.class);
            if (response == null || response.parsedData() == null) {
                throw new PermanentException("Resume parser returned an empty body");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw ClientSupport.map("resume parser", exception);
        } catch (ResourceAccessException exception) {
            throw new RetryableException("Could not reach resume parser", exception);
        }
    }
}
