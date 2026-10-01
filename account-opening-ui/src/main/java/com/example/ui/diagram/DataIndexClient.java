package com.example.ui.diagram;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads process/task state from the embedded Data Index GraphQL endpoint of
 * kogito-springboot-app. Called server side only, so the browser never talks to
 * port 8080 directly (no CORS, and the Data Index endpoint stays unexposed).
 */
@Component
public class DataIndexClient {

    /**
     * NodeInstance.definitionId is the BPMN element id (Task_1, Gateway_1, ...),
     * which is exactly what bpmn-js addresses elements by.
     */
    private static final String QUERY = """
            query($id: String!) {
              ProcessInstances(where: {id: {equal: $id}}) {
                id
                processId
                state
                nodes { name type definitionId enter exit }
              }
              UserTaskInstances(where: {processInstanceId: {equal: $id}}) {
                id
                name
                state
                actualOwner
                potentialGroups
                potentialUsers
              }
            }
            """;

    private final RestClient restClient;

    DataIndexClient(RestClient.Builder builder,
                    @Value("${kogito.api.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public Snapshot snapshot(String processInstanceId) {
        GraphQlResponse response = restClient.post()
                .uri("/graphql")
                .body(Map.of("query", QUERY, "variables", Map.of("id", processInstanceId)))
                .retrieve()
                .body(GraphQlResponse.class);

        if (response == null || response.data() == null) {
            return Snapshot.empty();
        }
        if (response.errors() != null && !response.errors().isEmpty()) {
            throw new IllegalStateException("Data Index GraphQL error: " + response.errors());
        }

        List<ProcessInstance> instances = nullSafe(response.data().ProcessInstances());
        if (instances.isEmpty()) {
            return Snapshot.empty();
        }
        ProcessInstance instance = instances.get(0);
        return new Snapshot(instance.processId(),
                instance.state(),
                nullSafe(instance.nodes()),
                nullSafe(response.data().UserTaskInstances()));
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }

    public record Snapshot(String processId,
                           String state,
                           List<NodeInstance> nodes,
                           List<TaskInstance> tasks) {

        static Snapshot empty() {
            return new Snapshot(null, null, List.of(), List.of());
        }

        public boolean isEmpty() {
            return state == null;
        }
    }

    /** A node instance; {@code exit == null} means it is still running. */
    public record NodeInstance(String name,
                               String type,
                               String definitionId,
                               String enter,
                               String exit) {
        public boolean active() {
            return exit == null;
        }
    }

    public record TaskInstance(String id,
                               String name,
                               String state,
                               String actualOwner,
                               List<String> potentialGroups,
                               List<String> potentialUsers) {
    }

    private record ProcessInstance(String id,
                                   String processId,
                                   String state,
                                   List<NodeInstance> nodes) {
    }

    private record GraphQlResponse(Data data, List<Map<String, Object>> errors) {
        @SuppressWarnings("checkstyle:MemberName")
        private record Data(List<ProcessInstance> ProcessInstances,
                            List<TaskInstance> UserTaskInstances) {
        }
    }
}
