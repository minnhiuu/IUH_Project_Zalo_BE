package com.bondhub.messageservice.util;

import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.model.AttachmentInfo;
import com.bondhub.messageservice.model.LinkPreview;
import com.bondhub.messageservice.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class SearchableTextBuilder {

    private static final String LINK_PREFIX = "[Link]";
    private static final String LINK_INVITE_TEXT = "Bấm vào đây để tham gia nhóm trên Bondhub";

    private SearchableTextBuilder() {}

    public static String build(Message message) {
        if (message.getType() == MessageType.SYSTEM) {
            return null;
        }

        List<String> parts = new ArrayList<>();

        if (message.getType() == MessageType.FILE) {
            addAttachmentNames(parts, message.getAttachments());
            return parts.isEmpty() ? null : String.join(" ", parts);
        }

        if (message.getType() == MessageType.LINK) {
            addLinkParts(parts, message.getLinkPreview());
            return parts.isEmpty() ? null : String.join(" ", parts);
        }

        if (message.getContent() != null && !message.getContent().isBlank()) {
            parts.add(message.getContent().trim());
        }

        addAttachmentNames(parts, message.getAttachments());

        LinkPreview linkPreview = message.getLinkPreview();
        if (linkPreview != null) {
            if (linkPreview.getUrl() != null && !linkPreview.getUrl().isBlank()) {
                parts.add(linkPreview.getUrl().trim());
            }
            if (linkPreview.getGroupName() != null && !linkPreview.getGroupName().isBlank()) {
                parts.add(linkPreview.getGroupName().trim());
            }
        }

        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private static void addLinkParts(List<String> parts, LinkPreview linkPreview) {
        parts.add(LINK_PREFIX);

        if (linkPreview != null && linkPreview.getGroupName() != null && !linkPreview.getGroupName().isBlank()) {
            parts.add(linkPreview.getGroupName().trim());
        }

        parts.add(LINK_INVITE_TEXT);

        if (linkPreview != null && linkPreview.getUrl() != null && !linkPreview.getUrl().isBlank()) {
            parts.add(linkPreview.getUrl().trim());
        }
    }

    private static void addAttachmentNames(List<String> parts, List<AttachmentInfo> attachments) {
        if (attachments == null) {
            return;
        }

        for (AttachmentInfo attachment : attachments) {
            String name = attachment.getOriginalFileName() != null
                    ? attachment.getOriginalFileName()
                    : attachment.getFileName();
            if (name != null && !name.isBlank()) {
                parts.add(name.trim());
            }
        }
    }
}
