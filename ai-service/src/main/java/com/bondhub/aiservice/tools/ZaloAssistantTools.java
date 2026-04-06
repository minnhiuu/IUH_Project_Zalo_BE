package com.bondhub.aiservice.tools;

import com.bondhub.aiservice.client.friendservice.FriendServiceClient;
import com.bondhub.aiservice.client.messageservice.MessageServiceClient;
import com.bondhub.aiservice.client.userservice.UserServiceClient;
import com.bondhub.aiservice.security.AiSecurityContextHolder;
import com.bondhub.aiservice.security.AiUserContextInterceptor;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.request.BioUpdateRequest;
import com.bondhub.common.dto.client.userservice.user.request.UserUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class ZaloAssistantTools {

    private final UserServiceClient userClient;
    private final FriendServiceClient friendClient;
    private final MessageServiceClient messageClient;
    private final ObjectMapper objectMapper;

    private static final ConcurrentHashMap<String, String> CONV_TO_USER = new ConcurrentHashMap<>();

    public static void registerConversation(String convId, String userId) {
        CONV_TO_USER.put(convId, userId);
        log.info("[Tools] Registered convId={} → userId={}", convId, userId);
    }

    public static void unregisterConversation(String convId) {
        CONV_TO_USER.remove(convId);
    }

    private void activateHeaders() {
        Map<String, String> headers = null;
        for (Map.Entry<String, String> entry : CONV_TO_USER.entrySet()) {
            headers = AiSecurityContextHolder.getByUserId(entry.getValue());
            if (headers != null) break;
        }
        if (headers != null) {
            AiUserContextInterceptor.FORWARD_HEADERS.set(headers);
            log.info("[Tool] Headers activated: {}", headers.keySet());
        } else {
            log.warn("[Tool] No headers found from any registered conversation!");
        }
    }

    private void deactivateHeaders() {
        AiUserContextInterceptor.FORWARD_HEADERS.remove();
    }

    // ══ USER TOOLS ════════════════════════════════════════════════════════════

    @Tool("Lấy thông tin cá nhân của người dùng đang đăng nhập: tên, email, số điện thoại, bio, avatar")
    public String getMyProfile() {
        log.info("[Tool] getMyProfile called");
        try {
            activateHeaders();
            var resp = userClient.getMyProfile();
            return toToolResult(resp, "profile người dùng");
        } catch (Exception e) {
            log.error("[Tool] getMyProfile failed: {}", e.getMessage());
            return "Không thể lấy thông tin người dùng lúc này: " + e.getMessage();
        } finally {
            deactivateHeaders();
        }
    }

    @Tool("Cập nhật thông tin hồ sơ cá nhân: họ tên, ngày sinh, bio, giới tính. Dùng khi user muốn thay đổi tên hoặc nhiều trường cùng lúc.")
    public String updateMyProfile(
            @P("Họ và tên đầy đủ (bắt buộc)") String fullName,
            @P("Ngày sinh định dạng yyyy-MM-dd (tuỳ chọn, để null nếu không cập nhật)") String dob,
            @P("Tiểu sử ngắn (tuỳ chọn, để null nếu không cập nhật)") String bio,
            @P("Giới tính: MALE, FEMALE hoặc OTHER (tuỳ chọn, để null nếu không cập nhật)") String gender) {
        log.info("[Tool] updateMyProfile called: fullName={}, dob={}, gender={}", fullName, dob, gender);
        try {
            activateHeaders();
            LocalDate parsedDob = (dob != null && !dob.isBlank()) ? LocalDate.parse(dob) : null;
            UserUpdateRequest request = UserUpdateRequest.builder()
                    .fullName(fullName)
                    .dob(parsedDob)
                    .bio(bio)
                    .gender(gender)
                    .build();
            var resp = userClient.updateMyProfile(request);
            return toToolResult(resp, "cập nhật hồ sơ");
        } catch (Exception e) {
            log.error("[Tool] updateMyProfile failed: {}", e.getMessage());
            return "Không thể cập nhật hồ sơ lúc này: " + e.getMessage();
        } finally {
            deactivateHeaders();
        }
    }

    @Tool("Cập nhật chỉ tiểu sử (bio) của người dùng đang đăng nhập. Dùng khi user chỉ muốn thay đổi bio/giới thiệu bản thân.")
    public String updateMyBio(@P("Nội dung bio mới muốn cập nhật") String bio) {
        log.info("[Tool] updateMyBio called with bio='{}'", bio);
        try {
            activateHeaders();
            var resp = userClient.updateMyBio(new BioUpdateRequest(bio));
            return toToolResult(resp, "cập nhật bio");
        } catch (Exception e) {
            log.error("[Tool] updateMyBio failed: {}", e.getMessage());
            return "Không thể cập nhật bio lúc này: " + e.getMessage();
        } finally {
            deactivateHeaders();
        }
    }

    // ══ FRIEND TOOLS ══════════════════════════════════════════════════════════


    @Tool("Lấy danh sách bạn bè hiện tại của người dùng đang đăng nhập")
    public String getMyFriends() {
        log.info("[Tool] getMyFriends called");
        try {
            activateHeaders();
            var resp = friendClient.getMyFriends(0, 50);
            return toToolResult(resp, "danh sách bạn bè");
        } catch (Exception e) {
            log.error("[Tool] getMyFriends failed: {}", e.getMessage());
            return "Không thể lấy danh sách bạn bè lúc này: " + e.getMessage();
        } finally {
            deactivateHeaders();
        }
    }

    @Tool("Đếm số bạn bè chung giữa người dùng hiện tại và một người dùng khác theo userId")
    public String countMutualFriends(@P("userId của người dùng cần kiểm tra") String userId) {
        log.info("[Tool] countMutualFriends called for userId={}", userId);
        try {
            activateHeaders();
            var resp = friendClient.countMutualFriends(userId);
            return toToolResult(resp, "số bạn bè chung");
        } catch (Exception e) {
            log.error("[Tool] countMutualFriends failed: {}", e.getMessage());
            return "Không thể đếm bạn bè chung lúc này: " + e.getMessage();
        } finally {
            deactivateHeaders();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONVERSATION TOOLS
    // ══════════════════════════════════════════════════════════════════════════

    @Tool("Lấy danh sách các phòng chat (cuộc hội thoại) của người dùng đang đăng nhập, bao gồm chat 1-1 và nhóm")
    public String getMyConversations() {
        log.info("[Tool] getMyConversations called");
        try {
            activateHeaders();
            var resp = messageClient.getMyConversations(0, 20);
            return toToolResult(resp, "danh sách phòng chat");
        } catch (Exception e) {
            log.error("[Tool] getMyConversations failed: {}", e.getMessage());
            return "Không thể lấy danh sách phòng chat lúc này: " + e.getMessage();
        } finally {
            deactivateHeaders();
        }
    }

    @Tool("Lấy các tin nhắn gần nhất trong một phòng chat cụ thể theo conversationId")
    public String getRecentMessages(@P("ID của phòng chat (conversationId)") String conversationId) {
        log.info("[Tool] getRecentMessages called for conversationId={}", conversationId);
        try {
            activateHeaders();
            var resp = messageClient.getMessages(conversationId, 0, 10);
            return toToolResult(resp, "tin nhắn phòng chat");
        } catch (Exception e) {
            log.error("[Tool] getRecentMessages failed: {}", e.getMessage());
            return "Không thể lấy tin nhắn phòng chat lúc này: " + e.getMessage();
        } finally {
            deactivateHeaders();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPER
    // ══════════════════════════════════════════════════════════════════════════

    private <T> String toToolResult(ApiResponse<T> resp, String dataLabel) {
        if (resp == null) {
            return "Không thể kết nối đến hệ thống lúc này. Vui lòng thử lại sau.";
        }
        if (resp.code() == 1000 && resp.data() != null) {
            try {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(resp.data());
            } catch (Exception e) {
                log.warn("[Tool] JSON serialization failed for {}: {}", dataLabel, e.getMessage());
                return resp.data().toString();
            }
        }
        String msg = resp.message() != null ? resp.message() : "Hệ thống đang gặp sự cố.";
        log.warn("[Tool] {} unavailable: code={}, message={}", dataLabel, resp.code(), msg);
        return msg;
    }
}
