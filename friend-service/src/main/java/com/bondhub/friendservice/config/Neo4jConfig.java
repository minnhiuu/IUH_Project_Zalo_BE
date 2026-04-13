package com.bondhub.friendservice.config;

import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

@Configuration
@EnableNeo4jRepositories(basePackages = "com.bondhub.friendservice.graph.repository")
@Slf4j
public class Neo4jConfig {

    @Bean
    CommandLineRunner neo4jConstraints(Driver driver) {
        return args -> {
            try (var session = driver.session()) {
                // Remove duplicate User nodes before creating constraint
                session.run("""
                    MATCH (u:User)
                    WITH u.id AS uid, COLLECT(u) AS nodes
                    WHERE SIZE(nodes) > 1
                    FOREACH (n IN TAIL(nodes) | DETACH DELETE n)
                """);
                // Remove duplicate Group nodes
                session.run("""
                    MATCH (g:Group)
                    WITH g.id AS gid, COLLECT(g) AS nodes
                    WHERE SIZE(nodes) > 1
                    FOREACH (n IN TAIL(nodes) | DETACH DELETE n)
                """);
                session.run("CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE");
                session.run("CREATE CONSTRAINT group_id_unique IF NOT EXISTS FOR (g:Group) REQUIRE g.id IS UNIQUE");
                log.info("Neo4j uniqueness constraints ensured for User(id) and Group(id)");
            }
        };
    }
}
