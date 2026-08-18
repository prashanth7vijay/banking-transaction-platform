package com.platform.dashboard.controller;

import com.platform.dashboard.dto.CommandCenterSummaryResponse;
import com.platform.dashboard.service.CommandCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** EMPLOYEE-only, same as the approval workbench and exceptions - this is an internal operations view. */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('EMPLOYEE')")
public class CommandCenterController {

    private final CommandCenterService commandCenterService;

    @GetMapping("/command-center")
    public CommandCenterSummaryResponse commandCenter() {
        return commandCenterService.getSummary();
    }
}
