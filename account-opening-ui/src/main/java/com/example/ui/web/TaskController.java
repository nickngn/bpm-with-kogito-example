package com.example.ui.web;

import com.example.ui.client.api.TasksApi;
import com.example.ui.client.model.TransitionInfo;
import com.example.ui.client.model.UserTaskView;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Renders a task's completion form from its JSON schema (AccountOpeningResource's
 * generated /accountOpening/{taskName}/schema endpoint) instead of per-task-type
 * Java models, so this UI works for any user task the process adds later too.
 */
@Controller
public class TaskController {

    private final TasksApi tasksApi;

    public TaskController(TasksApi tasksApi) {
        this.tasksApi = tasksApi;
    }

    @GetMapping("/tasks/{id}")
    public String show(@PathVariable String id, @AuthenticationPrincipal OidcUser oidcUser, Model model) {
        CurrentUser user = CurrentUser.from(oidcUser);
        UserTaskView task = tasksApi.getTask(id, user.username(), user.groups());

        String taskNameSlug = task.getTaskName().replace(' ', '_');
        Map<String, Object> schema = tasksApi.getTaskSchema(taskNameSlug);
        Map<String, Boolean> outputFields = new LinkedHashMap<>();
        Object properties = schema.get("properties");
        if (properties instanceof Map<?, ?> props) {
            props.forEach((name, def) -> {
                if (def instanceof Map<?, ?> fieldDef && Boolean.TRUE.equals(fieldDef.get("output"))) {
                    outputFields.put((String) name, "boolean".equals(fieldDef.get("type")));
                }
            });
        }

        model.addAttribute("user", user);
        model.addAttribute("task", task);
        model.addAttribute("outputFields", outputFields);
        return "task";
    }

    @PostMapping("/tasks/{id}/complete")
    public String complete(@PathVariable String id, @AuthenticationPrincipal OidcUser oidcUser,
            @RequestParam Map<String, String> form) {
        CurrentUser user = CurrentUser.from(oidcUser);

        Map<String, Object> data = new HashMap<>();
        form.forEach((key, value) -> {
            if (key.startsWith("field_")) {
                String name = key.substring("field_".length());
                data.put(name, "true".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) ? Boolean.TRUE
                        : "false".equalsIgnoreCase(value) ? Boolean.FALSE : value);
            }
        });

        tasksApi.transitionTask(id, user.username(), user.groups(),
                new TransitionInfo().transitionId("complete").data(data));
        return "redirect:/tasks";
    }
}
