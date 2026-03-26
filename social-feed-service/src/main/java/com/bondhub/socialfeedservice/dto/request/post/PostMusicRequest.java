package com.bondhub.socialfeedservice.dto.request.post;

/**
 * DTO for submitting full Jamendo track metadata when creating or updating a story post.
 * Mirrors the fields in {@link com.bondhub.socialfeedservice.model.embedded.PostMusic}.
 */
public record PostMusicRequest(
        String jamendoId,
        String title,
        String artistName,
        String audioUrl,
        String coverUrl,
        Integer duration,
        String albumName
) {
}
