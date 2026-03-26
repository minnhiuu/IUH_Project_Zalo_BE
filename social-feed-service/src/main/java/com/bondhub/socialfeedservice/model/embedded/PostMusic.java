package com.bondhub.socialfeedservice.model.embedded;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostMusic {
    String jamendoId;      // The 'id' from Jamendo
    String title;          // The 'name' of the track
    String artistName;     // The 'artist_name'
    String audioUrl;       // The 'audio' or 'audiodownload' link
    String coverUrl;       // The 'image' or 'album_image' link
    Integer duration;      // Duration in seconds
    String albumName;      // Optional: name of the album
}
