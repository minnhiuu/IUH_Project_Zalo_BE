package com.leafy.socialfeedservice.controller.internal;

import com.bondhub.common.dto.ApiResponse;
import com.leafy.socialfeedservice.service.seeder.SocialFeedSeederService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Internal controller for triggering mock-data seeding.
 * All routes under /internal/** are permitted without authentication (see SecurityConfig).
 * Intended for development/testing use only.
 */
@RestController
@RequestMapping("/social/internal/seeder")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Tag(name = "Internal Seeder", description = "Internal dev-seeding endpoints")
public class DataSeederController {

    SocialFeedSeederService seederService;

    @PostMapping(value = {"/seed/all", ""})
    @Operation(summary = "Full pipeline: fetch users → seed interests → seed Posts/Comments/Reactions")
    public ResponseEntity<ApiResponse<Map<String, Object>>> seedEverything() {
        return ResponseEntity.ok(ApiResponse.success(seederService.seedEverything()));
    }
}
