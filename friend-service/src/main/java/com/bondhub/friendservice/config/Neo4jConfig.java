package com.bondhub.friendservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableNeo4jRepositories(basePackages = "com.bondhub.friendservice.graph.repository")
public class Neo4jConfig {
}
