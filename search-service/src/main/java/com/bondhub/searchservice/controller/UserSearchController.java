package com.bondhub.searchservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.PageResponse;
import com.bondhub.common.dto.client.userservice.user.response.UserSummaryResponse;
import com.bondhub.searchservice.dto.response.UserSearchResponse;
import com.bondhub.searchservice.service.index.user.UserSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class UserSearchController {

    private final UserSearchService userSearchService;

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<PageResponse<List<UserSearchResponse>>>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "false") boolean debug) {

        PageResponse<List<UserSearchResponse>> response =
                userSearchService.searchUsersWithMetadata(keyword, page, size, debug);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/users/by-phones")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> findUsersByPhones(
            @RequestBody List<String> phones) {
        return ResponseEntity.ok(ApiResponse.success(userSearchService.findUsersByPhones(phones)));
    }
}
