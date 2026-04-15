package com.bondhub.friendservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.friendservice.dto.request.ContactImportRequest;
import com.bondhub.friendservice.dto.response.ContactImportResponse;
import com.bondhub.friendservice.service.contact.ContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/contacts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Contacts", description = "Contact import and management APIs")
public class ContactController {

    private final ContactService contactService;

    @PostMapping("/import")
    @Operation(summary = "Import contacts", description = "Import phone contacts to find existing users and build contact graph")
    public ResponseEntity<ApiResponse<ContactImportResponse>> importContacts(
            @Valid @RequestBody ContactImportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(contactService.importContacts(request)));
    }
}
