package com.bondhub.friendservice.graph.relationship;

import com.bondhub.friendservice.graph.node.UserNode;
import lombok.*;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InContactRelationship {

    @RelationshipId
    private Long id;

    @TargetNode
    private UserNode targetUser;

    private Double score;
    private String source; // "PHONE" or "EMAIL"
    private Long createdAt;
}
