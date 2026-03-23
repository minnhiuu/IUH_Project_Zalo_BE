package com.leafy.socialfeedservice.model.embedded;

import com.leafy.socialfeedservice.model.enums.ReactionType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PostStats {
    int reactionCount;
    int commentCount;
    int shareCount;

    @Builder.Default
    List<ReactionType> topReactions = new ArrayList<>();
}
