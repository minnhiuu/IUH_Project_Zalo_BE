package com.bondhub.aiservice.tools;

import com.bondhub.aiservice.client.friendservice.FriendServiceClient;
import com.bondhub.aiservice.client.messageservice.MessageServiceClient;
import com.bondhub.aiservice.client.userservice.UserServiceClient;
import com.bondhub.aiservice.util.HeaderUtil;
import com.bondhub.aiservice.util.StringUtil;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.dto.client.userservice.user.request.BioUpdateRequest;
import com.bondhub.common.dto.client.userservice.user.request.UserUpdateRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.bondhub.aiservice.util.HeaderUtil.*;
import static com.bondhub.aiservice.util.StringUtil.clean;

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



    // ══ USER TOOLS ════════════════════════════════════════════════════════════

    @Tool("Lấy thông tin cá nhân của người dùng đang đăng nhập: tên, email, số điện thoại, bio, avatar")
    public String getMyProfile() {
        log.info("[Tool] getMyProfile called");
        try {
            activateHeaders(CONV_TO_USER);
            var resp = userClient.getMyProfile();
            return toToolResult(resp, "profile người dùng");
        } catch (Exception e) {
            log.error("[Tool] getMyProfile failed: {}", e.getMessage());
            return "Không thể lấy thông tin người dùng lúc này: " + e.getMessage();
        } finally {
            deactivateHeaders();
        }
    }

    @Tool("Cập nhật thông tin hồ sơ cá nhân. Chỉ truyền các trường cần thay đổi, các trường khác để trống.")
    public String updateMyProfile(
            @P("Họ tên mới (chỉ truyền nếu muốn đổi)") String fullName,
            @P("Ngày sinh yyyy-MM-dd (chỉ truyền nếu muốn đổi)") String dob,
            @P("Tiểu sử (chỉ truyền nếu muốn đổi)") String bio,
            @P("Giới tính: MALE, FEMALE (chỉ truyền nếu muốn đổi)") String gender) {
        log.info("[Tool] updateMyProfile called: fullName={}, dob={}, bio={}, gender={}", fullName, dob, bio, gender);
        try {
            activateHeaders(CONV_TO_USER);

            UserUpdateRequest request = UserUpdateRequest.builder()
                    .fullName(clean(fullName)) 
                    .dob(StringUtils.hasText(clean(dob)) ? LocalDate.parse(clean(dob)) : null)
                    .bio(clean(bio))
                    .gender(clean(gender))
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
            activateHeaders(CONV_TO_USER);
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
            activateHeaders(CONV_TO_USER);
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
            activateHeaders(CONV_TO_USER);
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
            activateHeaders(CONV_TO_USER);
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
            activateHeaders(CONV_TO_USER);
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
