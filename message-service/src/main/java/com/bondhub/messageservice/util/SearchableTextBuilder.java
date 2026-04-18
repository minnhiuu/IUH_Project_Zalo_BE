package com.bondhub.messageservice.util;

import com.bondhub.common.enums.MessageType;
import com.bondhub.messageservice.model.AttachmentInfo;
import com.bondhub.messageservice.model.LinkPreview;
import com.bondhub.messageservice.model.Message;

import java.util.ArrayList;
import java.util.List;

public final class SearchableTextBuilder {

    private SearchableTextBuilder() {}

    public static String build(Message message) {
        if (message.getType() == MessageType.SYSTEM) {
            return null;
        }

        List<String> parts = new ArrayList<>();

        if (message.getContent() != null && !message.getContent().isBlank()) {
            parts.add(message.getContent().trim());
        }

        if (message.getAttachments() != null) {
            for (AttachmentInfo attachment : message.getAttachments()) {
                String name = attachment.getOriginalFileName() != null
                        ? attachment.getOriginalFileName()
                        : attachment.getFileName();
                if (name != null && !name.isBlank()) {
                    parts.add(name.trim());
                }
            }
        }

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
}
