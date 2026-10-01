package com.example.ui.web;

import com.example.ui.client.api.ProcessApi;
import com.example.ui.client.model.AccountOpeningInput;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProcessController {

    private final ProcessApi processApi;

    public ProcessController(ProcessApi processApi) {
        this.processApi = processApi;
    }

    @GetMapping("/start")
    public String startForm() {
        return "start";
    }

    @PostMapping("/start")
    public String start(@RequestParam String applicantName) {
        processApi.startAccountOpening(new AccountOpeningInput().applicantName(applicantName));
        return "redirect:/tasks";
    }
}
