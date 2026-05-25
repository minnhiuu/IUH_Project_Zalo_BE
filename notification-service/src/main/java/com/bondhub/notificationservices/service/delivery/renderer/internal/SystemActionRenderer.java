package com.bondhub.notificationservices.service.delivery.renderer.internal;

import com.bondhub.notificationservices.model.Notification;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SystemActionRenderer {

    public static String render(Notification notification, String locale) {
        String action = getString(notification, "action");
        String actorName = getString(notification, "actorName");
        if (actorName == null) actorName = "Hệ thống";

        boolean isVi = "vi".equalsIgnoreCase(locale);

        if (action == null) return isVi ? "Thông báo hệ thống" : "System notification";

        return switch (action) {
            case "UPDATE_NAME" -> renderUpdateName(notification, actorName, isVi);
            case "UPDATE_AVATAR" -> isVi ? "Nhóm đã thay đổi ảnh đại diện" : "Group avatar has been changed";
            case "UPDATE_SETTINGS" -> renderUpdateSettings(notification, actorName, isVi);
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

    private static String renderUpdateName(Notification notification, String actorName, boolean isVi) {
        String oldName = getString(notification, "oldName");
        String newName = getString(notification, "newName");
        if (oldName != null && newName != null && !newName.isBlank()) {
            return isVi ? "Nhóm đã đổi tên từ \"" + oldName + "\" → \"" + newName + "\""
                       : "Group renamed from \"" + oldName + "\" to \"" + newName + "\"";
        }
        if (newName != null && !newName.isBlank()) {
            return isVi ? "Nhóm đã đổi tên thành \"" + newName + "\""
                       : "Group renamed to \"" + newName + "\"";
        }
        return isVi ? actorName + " đã đổi tên nhóm" : actorName + " renamed the group";
    }

    private static String renderUpdateSettings(Notification notification, String actorName, boolean isVi) {
        String setting = getString(notification, "setting");
        String value = getString(notification, "value");

        if ("membershipApprovalEnabled".equals(setting)) {
            if ("true".equalsIgnoreCase(value)) {
                return isVi 
                    ? actorName + " đã bật tính năng phê duyệt thành viên. Trưởng/phó nhóm cần xác nhận yêu cầu tham gia nhóm"
                    : actorName + " enabled membership approval. Owner/admins must approve join requests";
            } else {
                return isVi 
                    ? actorName + " đã tắt tính năng phê duyệt thành viên. Người khác có thể tham gia qua link nhóm hoặc được các thành viên mời vào"
                    : actorName + " disabled membership approval. Anyone can join via group link or member invitation";
            }
        }
        
        if ("memberCanSendMessages".equals(setting)) {
            if ("false".equalsIgnoreCase(value)) {
                return isVi ? actorName + " đã chỉ cho phép trưởng/phó nhóm gửi tin nhắn" : actorName + " allowed only owner/admins to send messages";
            } else {
                return isVi ? actorName + " đã cho phép tất cả thành viên gửi tin nhắn" : actorName + " allowed all members to send messages";
            }
        }

        return isVi ? actorName + " đã cập nhật cài đặt nhóm" : actorName + " updated group settings";
    }

    private static String getString(Notification notification, String key) {
        if (notification.getPayload() == null) return null;
        
        Object value = notification.getPayload().get(key);
        if (value != null) return value.toString();
        
        Object metaObj = notification.getPayload().get("metadata");
        if (metaObj instanceof java.util.Map<?, ?> metaMap) {
            Object metaVal = metaMap.get(key);
            if (metaVal != null) return metaVal.toString();
            
            Object innerPayload = metaMap.get("payload");
            if (innerPayload instanceof java.util.Map<?, ?> innerMap) {
                Object innerVal = innerMap.get(key);
                if (innerVal != null) return innerVal.toString();
            }
        }
        
        return null;
    }
}
