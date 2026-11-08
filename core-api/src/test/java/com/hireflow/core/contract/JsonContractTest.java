package com.hireflow.core.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.hireflow.core.client.ParseResumeResponse;
import com.hireflow.core.client.ScoreRequest;
import com.hireflow.core.client.ScoreResponse;
import com.hireflow.core.domain.BlindCandidate;
import com.hireflow.core.domain.Education;
import com.hireflow.core.domain.ParsedResume;
import com.hireflow.core.domain.WorkExperience;
import com.networknt.schema.Error;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    private static final SchemaRegistry SCHEMAS =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7);

    private static final Path CONTRACTS = contractsDirectory();

    private static final UUID FIRST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    void parseResumeResponseMatchesContract() throws Exception {
        JsonNode schema = readSchema("parser/parse-resume-response.schema.json");

        assertProperties(schema, ParseResumeResponse.class);
        assertProperties(schema.at("/properties/parsed_data"), ParsedResume.class);
        assertProperties(schema.at("/properties/parsed_data/properties/work_history/items"), WorkExperience.class);
        assertProperties(schema.at("/properties/parsed_data/properties/education/items"), Education.class);

        assertValid(schema, parseResumeResponse());
    }

    @Test
    void scoreRequestMatchesContract() throws Exception {
        JsonNode schema = readSchema("matcher/score-request.schema.json");

        assertProperties(schema, ScoreRequest.class);
        assertProperties(schema.at("/properties/candidate"), BlindCandidate.class);
        assertProperties(schema.at("/properties/candidate/properties/work_history/items"), WorkExperience.class);
        assertProperties(schema.at("/properties/candidate/properties/education/items"), Education.class);

        assertValid(schema, scoreRequest());
    }

    @Test
    void scoreResponseMatchesContract() throws Exception {
        JsonNode schema = readSchema("matcher/score-response.schema.json");

        assertProperties(schema, ScoreResponse.class);

        assertValid(schema, scoreResponse());
    }

    private static void assertProperties(JsonNode schema, Class<?> type) {
        assertThat(schema.path("type").asText()).as(type.getSimpleName()).isEqualTo("object");
        Set<String> schemaNames = fieldNames(schema.path("properties"));
        Set<String> jacksonNames = jacksonPropertyNames(type);
        assertThat(jacksonNames).as(type.getSimpleName()).isEqualTo(schemaNames);
        assertThat(jacksonNames).as(type.getSimpleName() + " required").containsAll(requiredNames(schema));
    }

    private static void assertValid(JsonNode schema, Object example) throws Exception {
        String json = MAPPER.writeValueAsString(example);
        List<Error> errors = SCHEMAS.getSchema(schema).validate(json, InputFormat.JSON);
        assertThat(errors).as(json).isEmpty();
        assertThat(MAPPER.treeToValue(MAPPER.readTree(json), example.getClass())).isEqualTo(example);
    }

    private static Set<String> jacksonPropertyNames(Class<?> type) {
        Set<String> names = new LinkedHashSet<>();
        for (BeanPropertyDefinition property : MAPPER
                .getSerializationConfig()
                .introspect(MAPPER.constructType(type))
                .findProperties()) {
            names.add(property.getName());
        }
        return names;
    }

    private static Set<String> fieldNames(JsonNode properties) {
        Set<String> names = new LinkedHashSet<>();
        properties.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static Set<String> requiredNames(JsonNode schema) {
        Set<String> required = new LinkedHashSet<>();
        schema.path("required").forEach(name -> required.add(name.asText()));
        return required;
    }

    private static JsonNode readSchema(String relative) {
        try {
            return MAPPER.readTree(CONTRACTS.resolve(relative).toFile());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    // Maven's working directory is the module. Walk up so a build from the repo root finds contracts/.
    private static Path contractsDirectory() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path contracts = dir.resolve("contracts");
            if (Files.isDirectory(contracts.resolve("parser")) && Files.isDirectory(contracts.resolve("matcher"))) {
                return contracts;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("contracts directory not found from " + Path.of("").toAbsolutePath());
    }

    private static ParseResumeResponse parseResumeResponse() {
        return new ParseResumeResponse("extracted text", parsedResume());
    }

    private static ParsedResume parsedResume() {
        return new ParsedResume(
                "Maya Chen",
                "maya@example.com",
                "555-0100",
                List.of("Java"),
                6.5,
                List.of(work()),
                List.of(education()));
    }

    private static ScoreRequest scoreRequest() {
        return new ScoreRequest(FIRST_ID, "Backend engineer", BlindCandidate.from(parsedResume()));
    }

    private static ScoreResponse scoreResponse() {
        return new ScoreResponse(
                FIRST_ID,
                80.0,
                70.0,
                60.0,
                74.5,
                List.of("Java"),
                List.of("Go"),
                List.of("backend services"),
                List.of("short tenure"),
                "solid backend fit",
                "v1",
                "mock");
    }

    private static WorkExperience work() {
        return new WorkExperience("Acme", "Engineer", "2020 - 2024", "Built APIs");
    }

    private static Education education() {
        return new Education("State University", "B.S.", "Computer Science", "2016 - 2020");
    }
}
