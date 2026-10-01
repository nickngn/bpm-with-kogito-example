package com.example.ui.web;

import com.example.ui.client.api.TasksApi;
import com.example.ui.client.model.TransitionInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class TaskInboxController {

    private final TasksApi tasksApi;

    public TaskInboxController(TasksApi tasksApi) {
        this.tasksApi = tasksApi;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/tasks";
    }

    @GetMapping("/tasks")
    public String inbox(@org.springframework.security.core.annotation.AuthenticationPrincipal OidcUser oidcUser, Model model) {
        CurrentUser user = CurrentUser.from(oidcUser);
        model.addAttribute("user", user);
        model.addAttribute("tasks", tasksApi.listTasks(user.username(), user.groups()));
        return "inbox";
    }

    @PostMapping("/tasks/{id}/claim")
    public String claim(@PathVariable String id, @org.springframework.security.core.annotation.AuthenticationPrincipal OidcUser oidcUser) {
        CurrentUser user = CurrentUser.from(oidcUser);
        tasksApi.transitionTask(id, user.username(), user.groups(), new TransitionInfo().transitionId("claim"));
        return "redirect:/tasks";
    }

    @PostMapping("/tasks/{id}/release")
    public String release(@PathVariable String id, @org.springframework.security.core.annotation.AuthenticationPrincipal OidcUser oidcUser) {
        CurrentUser user = CurrentUser.from(oidcUser);
        tasksApi.transitionTask(id, user.username(), user.groups(), new TransitionInfo().transitionId("release"));
        return "redirect:/tasks";
    }
}
