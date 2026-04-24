package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.messageservice.dto.response.MessageSyncResponse;
import com.bondhub.messageservice.service.message.MessageInternalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/messages")
public class InternalMessageController {

    private final MessageInternalService messageInternalService;

    @GetMapping("/sync/batch")
    public ResponseEntity<ApiResponse<List<MessageSyncResponse>>> getMessagesBatch(
            @RequestParam(required = false) String lastId,
            @RequestParam(defaultValue = "1000") int batchSize) {
        return ResponseEntity.ok(ApiResponse.success(
                messageInternalService.getMessagesBatch(lastId, batchSize)));
    }

    @GetMapping("/sync/count")
    public ResponseEntity<ApiResponse<Long>> getMessageCount() {
        return ResponseEntity.ok(ApiResponse.success(
                messageInternalService.getMessageCount()));
    }
}
