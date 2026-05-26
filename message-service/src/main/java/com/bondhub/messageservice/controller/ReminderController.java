package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.SecurityUtil;
import com.bondhub.messageservice.dto.request.ReminderRequest;
import com.bondhub.messageservice.dto.response.ReminderResponse;
import com.bondhub.messageservice.service.reminder.ReminderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;
    private final SecurityUtil securityUtil;

    @PostMapping
    public ApiResponse<ReminderResponse> createReminder(@RequestBody @Valid ReminderRequest request) {
        String userId = securityUtil.getCurrentUserId();
        return ApiResponse.success(reminderService.createReminder(request, userId));
    }

    @PutMapping("/{id}")
    public ApiResponse<ReminderResponse> updateReminder(@PathVariable String id, @RequestBody @Valid ReminderRequest request) {
        String userId = securityUtil.getCurrentUserId();
        return ApiResponse.success(reminderService.updateReminder(id, request, userId));
    }

    @GetMapping("/conversations/{conversationId}")
    public ApiResponse<List<ReminderResponse>> getReminders(@PathVariable String conversationId) {
        return ApiResponse.success(reminderService.getReminders(conversationId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteReminder(@PathVariable String id) {
        String userId = securityUtil.getCurrentUserId();
        reminderService.deleteReminder(id, userId);
        return ApiResponse.success(null);
    }
}