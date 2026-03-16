package com.bondhub.friendservice.model;

import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * Embedded document representing user's blocking preferences
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BlockPreference {

    @Builder.Default
    boolean message = true; // Block messages by default when blocking a user

    @Builder.Default
    boolean call = true;    // Block calls by default when blocking a user

    @Builder.Default
    boolean story = true;   // Block stories by default when blocking a user
}
