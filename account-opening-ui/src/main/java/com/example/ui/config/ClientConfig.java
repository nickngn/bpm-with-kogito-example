package com.example.ui.config;

import com.example.ui.client.ApiClient;
import com.example.ui.client.api.ProcessApi;
import com.example.ui.client.api.TasksApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClientConfig {

    @Bean
    public ApiClient apiClient(@Value("${kogito.api.base-url}") String baseUrl) {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(baseUrl);
        return apiClient;
    }

    @Bean
    public ProcessApi processApi(ApiClient apiClient) {
        return new ProcessApi(apiClient);
    }

    @Bean
    public TasksApi tasksApi(ApiClient apiClient) {
        return new TasksApi(apiClient);
    }
}
