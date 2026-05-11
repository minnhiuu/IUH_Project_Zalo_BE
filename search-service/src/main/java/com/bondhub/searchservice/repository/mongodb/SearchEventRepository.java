package com.bondhub.searchservice.repository.mongodb;

import com.bondhub.searchservice.model.mongodb.SearchEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchEventRepository extends MongoRepository<SearchEvent, String> {
}
