package com.bondhub.searchservice.repository;

import com.bondhub.searchservice.model.elasticsearch.UserIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSearchRepository extends ElasticsearchRepository<UserIndex, String> {
}
