package com.bondhub.searchservice.service.index.user;

import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.enums.SearchIndexType;
import com.bondhub.searchservice.service.failevent.FailedEventService;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.common.utils.LocalizationUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.bondhub.searchservice.service.index.core.AbstractMonitorService;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserMonitorServiceImpl extends AbstractMonitorService {

    public UserMonitorServiceImpl(
            ElasticsearchOperations esOps,
            ElasticsearchClient esClient,
            ElasticsearchProperties esProperties,
            LocalizationUtil localizationUtil,
            FailedEventService failedEventService) {
        super(esOps, esClient, esProperties, localizationUtil, failedEventService);
    }

    @Override
    public SearchIndexType getType() {
        return SearchIndexType.USER;
    }

    @Override
    public String getAlias() {
        return esProperties.getUserAlias();
    }

    @Override
    public Class<?> getIndexClass() {
        return com.bondhub.searchservice.model.elasticsearch.UserIndex.class;
    }

}
