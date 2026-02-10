package com.bondhub.authservice.controller;

import com.bondhub.authservice.service.seeder.AccountSeederService;
import com.bondhub.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth/internal/seed")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class DataSeederController {

    private final AccountSeederService accountSeederService;

    @PostMapping("/accounts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> seedAccounts(
            @RequestParam(defaultValue = "10") int count) {
        
        log.info("📥 Received seed request for {} accounts with fuzzy names", count);

        Map<String, Object> result = accountSeederService.seedAccounts(count);

        return ResponseEntity.ok(ApiResponse.success(result));

    }
}
