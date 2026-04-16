package com.bondhub.notificationservices.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.notificationservices.dto.request.template.CreateTemplateRequest;
import com.bondhub.notificationservices.dto.request.template.UpdateTemplateRequest;
import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.common.enums.NotificationType;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notification-templates")
@RequiredArgsConstructor
@Slf4j
public class NotificationTemplateController {

    private final NotificationTemplateService service;

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationTemplateResponse>> create(@Valid @RequestBody CreateTemplateRequest request) {
        log.info("API - Create template type={} locale={}", request.type(), request.locale());
        return ResponseEntity.ok(ApiResponse.success(service.create(request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<NotificationTemplateResponse>> update(@PathVariable String id, @Valid @RequestBody UpdateTemplateRequest request) {
        log.info("API - Update template id={}", id);
        return ResponseEntity.ok(ApiResponse.success(service.update(id, request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationTemplateResponse>> get(@RequestParam NotificationType type, @RequestParam NotificationChannel channel, @RequestParam String locale) {
        log.debug("API - Get template type={} locale={}", type, locale);
        return ResponseEntity.ok(ApiResponse.success(service.getTemplate(type, channel, locale)));
    }
}