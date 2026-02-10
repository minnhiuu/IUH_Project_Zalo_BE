package com.bondhub.userservice.repository;

import com.bondhub.userservice.model.elasticsearch.UserIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface UserSearchRepository extends ElasticsearchRepository<UserIndex, String> {
    List<UserIndex> findByPhoneNumber(String phoneNumber);
    List<UserIndex> findByAccountId(String accountId);
}
