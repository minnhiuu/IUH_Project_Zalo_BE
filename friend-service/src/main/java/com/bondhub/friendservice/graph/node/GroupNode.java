package com.bondhub.friendservice.graph.node;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("Group")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupNode {

    @Id
    private String id;
}
