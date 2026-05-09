package com.bondhub.notificationservices.service.delivery;

import com.bondhub.notificationservices.dto.response.template.NotificationTemplateResponse;
import com.bondhub.notificationservices.enums.NotificationChannel;
import com.bondhub.notificationservices.model.Notification;
import com.bondhub.notificationservices.service.template.NotificationTemplateService;
import com.bondhub.notificationservices.service.user.preference.UserPreferenceService;
import com.bondhub.common.enums.NotificationType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NotificationStrategyHelper {

    private static final int FCM_CHAT_VISIBLE_LINES = 4;

    NotificationTemplateService templateService;
    UserPreferenceService userPreferenceService;

    @Builder
    public record RenderedContent(String title, String body, String locale) {}

    public RenderedContent render(Notification notification, NotificationChannel channel, String forceLocale) {
        String recipientId = notification.getUserId();
        
        // 1. Determine Locale
        String locale = forceLocale;
        if (locale == null) {
            locale = userPreferenceService.getLocale(recipientId);
        }
        if (locale == null) locale = "vi";

        // 2. Prepare Render Data
        Map<String, Object> renderData = prepareRenderData(notification);

        // 3. Render (Bypass template for transient chat messages)
        String title;
        String body;

        if (notification.getType() == NotificationType.MESSAGE_DIRECT || 
            notification.getType() == NotificationType.MESSAGE_GROUP) {
            
            title = renderChatTitle(notification);
            body = renderChatBody(notification, channel);
            
        } else if (notification.getType() == NotificationType.SYSTEM) {
            // For SYSTEM messages:
            // 1. In Websocket: Let FE handle it (body = "")
            // 2. In Push: Try to get a localized template for this action
            title = renderChatTitle(notification);
            
            if (channel != NotificationChannel.FCM) {
                body = "";
            } else {
                try {
                    // Try to fetch a specific template for the action if possible
                    // Fallback to localized manual rendering if template not found
                    body = renderSystemAction(notification, locale);
                } catch (Exception e) {
                    body = "Thông báo hệ thống";
                }
            }
        } else {
            // Get Template for other types
            NotificationTemplateResponse template = templateService.getTemplate(
                    notification.getType(),
                    channel,
                    locale
            );
            title = templateService.render(template.titleTemplate(), renderData);
            body = templateService.render(template.bodyTemplate(), renderData);
        }

        return RenderedContent.builder()
                .title(title)
                .body(body)
                .locale(locale)
                .build();
    }

    public String renderChatTitle(Notification notification) {
        boolean isGroup = Boolean.TRUE.equals(notification.getPayload().get("isGroup"));
        if (isGroup || notification.getType() == NotificationType.SYSTEM) {
            String groupName = getStr(notification, "groupName");
            if (groupName != null && !groupName.isBlank()) return groupName;
            
            return notification.getType() == NotificationType.SYSTEM ? "Hệ thống" : "Nhóm mới";
        }
        return getStr(notification, "actorName");
    }

    public String resolveAvatar(Notification notification, String baseUrl) {
        // 1. Try group avatar first for group-related system events
        String convAvt = getStr(notification, "conversationAvatar");
        if (convAvt != null && !convAvt.isEmpty()) {
            return convAvt.startsWith("http") ? convAvt : baseUrl + convAvt;
        }

        // 2. Fallback to actor avatar
        String actorAvt = getStr(notification, "actorAvatar");
        if (actorAvt != null && !actorAvt.isEmpty()) {
            return actorAvt.startsWith("http") ? actorAvt : baseUrl + actorAvt;
        }

        return null;
    }

    private String renderChatBody(Notification notification, NotificationChannel channel) {
        // 1. Try to use aggregated snippets if available
        Object snippetsObj = notification.getPayload().get("snippets");
        if (snippetsObj instanceof java.util.List<?> snippets && !snippets.isEmpty()) {
            var lines = snippets.stream()
                    .map(Object::toString)
                    .map(line -> line.replaceAll("\\s+", " ").trim())
                    .filter(line -> !line.isEmpty())
                    .toList();

            if (channel == NotificationChannel.FCM && lines.size() > FCM_CHAT_VISIBLE_LINES) {
                lines = lines.subList(lines.size() - FCM_CHAT_VISIBLE_LINES, lines.size());
            }

            return String.join("\n", lines);
        }

        // 2. Fallback to single message rendering
        boolean isGroup = Boolean.TRUE.equals(notification.getPayload().get("isGroup"));
        String content = getStr(notification, "content");
        if (isGroup) {
            String actorName = getStr(notification, "actorName");
            return actorName + ": " + content;
        }
        return content;
    }

    private String renderSystemAction(Notification notification, String locale) {
        String action = getStr(notification, "action");
        String actorName = getStr(notification, "actorName");
        if (actorName == null) actorName = "Hệ thống";

        boolean isVi = "vi".equalsIgnoreCase(locale);

        if (action == null) return isVi ? "Thông báo hệ thống" : "System notification";

        return switch (action) {
            case "UPDATE_NAME" -> {
                String oldName = getStr(notification, "oldName");
                String newName = getStr(notification, "newName");
                if (oldName != null && newName != null && !newName.isBlank()) {
                    yield isVi ? "Nhóm đã đổi tên từ \"" + oldName + "\" → \"" + newName + "\""
                               : "Group renamed from \"" + oldName + "\" to \"" + newName + "\"";
                }
                if (newName != null && !newName.isBlank()) {
                    yield isVi ? "Nhóm đã đổi tên thành \"" + newName + "\""
                               : "Group renamed to \"" + newName + "\"";
                }
                yield isVi ? actorName + " đã đổi tên nhóm" : actorName + " renamed the group";
            }
            case "UPDATE_AVATAR" -> isVi ? "Nhóm đã thay đổi ảnh đại diện" : "Group avatar has been changed";
            case "UPDATE_SETTINGS" -> {
                String setting = getStr(notification, "setting");
                String value = getStr(notification, "value");

                if ("membershipApprovalEnabled".equals(setting)) {
                    if ("true".equalsIgnoreCase(value)) {
                        yield isVi 
                            ? actorName + " đã bật tính năng phê duyệt thành viên. Trưởng/phó nhóm cần xác nhận yêu cầu tham gia nhóm"
                            : actorName + " enabled membership approval. Owner/admins must approve join requests";
                    } else {
                        yield isVi 
                            ? actorName + " đã tắt tính năng phê duyệt thành viên. Người khác có thể tham gia qua link nhóm hoặc được các thành viên mời vào"
                            : actorName + " disabled membership approval. Anyone can join via group link or member invitation";
                    }
                }
                
                if ("memberCanSendMessages".equals(setting)) {
                    if ("false".equalsIgnoreCase(value)) {
                        yield isVi ? actorName + " đã chỉ cho phép trưởng/phó nhóm gửi tin nhắn" : actorName + " allowed only owner/admins to send messages";
                    } else {
                        yield isVi ? actorName + " đã cho phép tất cả thành viên gửi tin nhắn" : actorName + " allowed all members to send messages";
                    }
                }

                yield isVi ? actorName + " đã cập nhật cài đặt nhóm" : actorName + " updated group settings";
            }
            case "ADD_MEMBERS" -> isVi ? actorName + " đã thêm thành viên mới" : actorName + " added new members";
            case "JOIN_BY_LINK" -> isVi ? actorName + " đã tham gia qua liên kết" : actorName + " joined via link";
            case "GENERATE_JOIN_LINK" -> isVi ? actorName + " đã tạo link tham gia nhóm" : actorName + " created a group join link";
            case "DISABLE_JOIN_LINK" -> isVi ? actorName + " đã hủy link tham gia nhóm" : actorName + " disabled the group join link";
            case "REFRESH_JOIN_LINK" -> isVi ? actorName + " đã làm mới link tham gia nhóm" : actorName + " refreshed the group join link";
            case "REMOVE_MEMBER" -> isVi ? actorName + " đã xóa thành viên khỏi nhóm" : actorName + " removed a member";
            case "LEAVE_GROUP" -> isVi ? actorName + " đã rời khỏi nhóm" : actorName + " left the group";
            case "DISBAND_GROUP" -> isVi ? actorName + " đã giải tán nhóm" : actorName + " disbanded the group";
            case "PROMOTE_ADMIN" -> isVi ? actorName + " đã bổ nhiệm phó nhóm mới" : actorName + " promoted a new admin";
            case "DEMOTE_ADMIN" -> isVi ? actorName + " đã gỡ quyền phó nhóm" : actorName + " demoted an admin";
            case "TRANSFER_OWNER" -> isVi ? actorName + " đã chuyển quyền trưởng nhóm" : actorName + " transferred group ownership";
            case "PIN_MESSAGE" -> isVi ? actorName + " đã ghim một tin nhắn" : actorName + " pinned a message";
            case "UNPIN_MESSAGE" -> isVi ? actorName + " đã bỏ ghim tin nhắn" : actorName + " unpinned a message";
            default -> isVi ? "Thông báo mới từ hệ thống" : "New system notification";
        };
    }

    private Map<String, Object> prepareRenderData(Notification notification) {
        int actorCount = notification.getActorIds() != null ? notification.getActorIds().size() : 0;
        String lastActorName = getStr(notification, "actorName");
        String lastActorAvatar = getStr(notification, "actorAvatar");

        Map<String, Object> data = new HashMap<>(notification.getPayload() != null ? notification.getPayload() : Collections.emptyMap());
        data.put("actorCount", actorCount);
        data.put("othersCount", actorCount > 2 ? actorCount - 1 : 0);
        data.put("showSecondActor", actorCount == 2);
        data.put("actorName", lastActorName != null ? lastActorName : "");
        data.put("actorAvatar", lastActorAvatar != null ? lastActorAvatar : "");

        if (actorCount == 2) {
            String secondActorName = getStr(notification, "secondActorName");
            data.put("secondActorName", secondActorName != null ? secondActorName : "một người khác");
        }
        
        return data;
    }

    public String getStr(Notification n, String key) {
        if (n.getPayload() == null) return null;
        
        // 1. Try top-level payload
        Object v = n.getPayload().get(key);
        if (v != null) return v.toString();
        
        // 2. Try inside "metadata" map if exists
        Object metaObj = n.getPayload().get("metadata");
        if (metaObj instanceof Map<?, ?> metaMap) {
            // 2.1. Check directly in metadata
            Object metaVal = metaMap.get(key);
            if (metaVal != null) return metaVal.toString();
            
            // 2.2. Check inside "payload" map within metadata (for deep nesting)
            Object innerPayload = metaMap.get("payload");
            if (innerPayload instanceof Map<?, ?> innerMap) {
                Object innerVal = innerMap.get(key);
                if (innerVal != null) return innerVal.toString();
            }
        }
        
        return null;
    }
}
