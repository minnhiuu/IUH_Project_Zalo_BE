package com.bondhub.notificationservices.service.delivery.renderer.internal;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MediaLabelBuilder {

    public static String buildImageLabel(int count, String locale) {
        boolean english = "en".equalsIgnoreCase(locale);
        if (count <= 0) return "";
        return count > 1 ? (english ? "Photos" : "Nhiều ảnh") : (english ? "Photo" : "Ảnh");
    }

    public static String buildVideoLabel(int count, String locale) {
        boolean english = "en".equalsIgnoreCase(locale);
        if (count <= 0) return "";
        return count > 1 ? (english ? "videos" : "nhiều video") : "video";
    }

    public static String buildMediaLabel(int imageCount, int videoCount, String locale) {
        boolean english = "en".equalsIgnoreCase(locale);

        if (imageCount > 0 && videoCount > 0) {
            String imageLabel = imageCount > 1
                    ? (english ? "Photos" : "Nhiều ảnh")
                    : (english ? "Photo" : "Ảnh");
            String videoLabel = videoCount > 1
                    ? (english ? "videos" : "nhiều video")
                    : "video";
            return "[" + imageLabel + (english ? " and " : " và ") + videoLabel + "]";
        }

        if (imageCount > 0) {
            return imageCount > 1
                    ? (english ? "[Photos]" : "[Nhiều ảnh]")
                    : (english ? "[Photo]" : "[Ảnh]");
        }

        if (videoCount > 0) {
            return videoCount > 1
                    ? (english ? "[Videos]" : "[Nhiều video]")
                    : "[Video]";
        }

        return "[Other]";
    }
}
