package com.bondhub.searchservice.repository.elastic;

import com.bondhub.searchservice.model.elasticsearch.MessageIndex;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageSearchRepository extends ElasticsearchRepository<MessageIndex, String> {
}
