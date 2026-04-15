package com.bondhub.messageservice.controller;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.messageservice.dto.request.CallRequest;
import com.bondhub.messageservice.dto.response.CallResponse;
import com.bondhub.messageservice.service.call.CallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/calls")
@Tag(name = "Video Call", description = "Video Call 1-1 REST API")
public class CallController {

    CallService callService;

    @PostMapping("/initiate")
    @Operation(summary = "Initiate a 1-1 video call")
    public ResponseEntity<ApiResponse<CallResponse>> initiateCall(@Valid @RequestBody CallRequest request) {
        return ResponseEntity.ok(ApiResponse.success(callService.initiateCall(request)));
    }

    @PostMapping("/{sessionId}/accept")
    @Operation(summary = "Accept an incoming call")
    public ResponseEntity<ApiResponse<CallResponse>> acceptCall(@PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(callService.acceptCall(sessionId)));
    }

    @PostMapping("/{sessionId}/reject")
    @Operation(summary = "Reject an incoming call")
    public ResponseEntity<ApiResponse<Void>> rejectCall(@PathVariable String sessionId) {
        callService.rejectCall(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{sessionId}/end")
    @Operation(summary = "End an ongoing call")
    public ResponseEntity<ApiResponse<Void>> endCall(@PathVariable String sessionId) {
        callService.endCall(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{sessionId}/cancel")
    @Operation(summary = "Cancel an outgoing call (missed/timeout)")
    public ResponseEntity<ApiResponse<Void>> cancelCall(@PathVariable String sessionId) {
        callService.cancelCall(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{sessionId}/token")
    @Operation(summary = "Get or refresh RTC token for a call session")
    public ResponseEntity<ApiResponse<CallResponse>> getCallToken(@PathVariable String sessionId) {
        return ResponseEntity.ok(ApiResponse.success(callService.getCallToken(sessionId)));
    }
}
