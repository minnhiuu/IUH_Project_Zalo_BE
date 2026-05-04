package com.bondhub.searchservice.service.index.message;

import com.bondhub.common.dto.ApiResponse;
import com.bondhub.common.utils.LocalizationUtil;
import com.bondhub.searchservice.client.MessageServiceClient;
import com.bondhub.searchservice.config.ElasticsearchProperties;
import com.bondhub.searchservice.dto.response.*;
import com.bondhub.searchservice.enums.*;
import com.bondhub.searchservice.model.elasticsearch.MessageIndex;
import com.bondhub.searchservice.service.index.core.AbstractSyncService;
import com.bondhub.searchservice.service.index.core.ReindexTaskTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class MessageSyncServiceImpl extends AbstractSyncService<MessageIndex> implements MessageSyncService {

    private final MessageServiceClient messageServiceClient;

    public MessageSyncServiceImpl(
            ElasticsearchOperations esOps,
            co.elastic.clients.elasticsearch.ElasticsearchClient esClient,
            ElasticsearchProperties esProperties,
            ReindexTaskTracker taskTracker,
            LocalizationUtil localizationUtil,
            MessageServiceClient messageServiceClient) {
        super(esOps, esClient, esProperties, taskTracker, localizationUtil);
        this.messageServiceClient = messageServiceClient;
    }

    @Override
    public SearchIndexType getType() {
        return SearchIndexType.MESSAGE;
    }

    @Override
    public String getAlias() {
        return esProperties.getMessageAlias();
    }

    @Override
    public Class<MessageIndex> getIndexClass() {
        return MessageIndex.class;
    }

    @Override
    protected long getTotalDocsCount() {
        try {
            ApiResponse<Long> response = messageServiceClient.getMessageCount();
            if (response != null && response.data() != null) {
                return response.data();
            }
        } catch (Exception e) {
            log.warn("Failed to get message count from message-service: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    protected void fullSyncToIndex(String indexName, String taskId, long totalDocs) {
        String lastId = null;
        long processed = 0;
        int batchSize = esProperties.getSync().getBatchSize();

        log.info("Starting full sync for messages, task {} to index {}", taskId, indexName);

        while (true) {
            ApiResponse<List<MessageSyncResponse>> response = messageServiceClient.getMessagesBatch(lastId, batchSize);

            if (response == null || response.data() == null || response.data().isEmpty()) {
                break;
            }

            List<MessageSyncResponse> messages = response.data();

            List<MessageIndex> docs = messages.stream()
                    .map(msg -> MessageIndex.builder()
                            .id(msg.id())
                            .conversationId(msg.conversationId())
                            .participantIds(msg.participantIds())
                            .participantNames(msg.participantNames())
                            .participantAvatars(msg.participantAvatars())
                            .conversationName(msg.conversationName())
                            .conversationAvatar(msg.conversationAvatar())
                            .group(msg.group())
                            .senderId(msg.senderId())
                            .senderName(msg.senderName())
                            .senderAvatar(msg.senderAvatar())
                            .content(msg.content())
                            .searchableText(msg.searchableText() != null ? msg.searchableText() : msg.content())
                            .conversationSearchText(msg.conversationSearchText())
                            .type(msg.type())
                            .status(msg.status())
                            .hasAttachment(msg.hasAttachment())
                            .hasLink(msg.hasLink())
                            .linkGroupName(msg.linkGroupName())
                            .linkUrl(msg.linkUrl())
                            .originalFileName(msg.originalFileName())
                            .size(msg.size())
                            .createdAt(msg.createdAt())
                            .deletedBy(msg.deletedBy())
                            .visibleTo(msg.visibleTo())
                            .build())
                    .toList();

            bulkIndex(docs, indexName);

            lastId = messages.getLast().id();
            processed += messages.size();

            ReindexStep currentStep = ReindexStep.SYNCING_START;
            if (totalDocs > 0) {
                double currentRatio = (double) processed / totalDocs;
                if (currentRatio > 0.8) currentStep = ReindexStep.DATA_COMPLETE;
                else if (currentRatio > 0.4) currentStep = ReindexStep.SYNCING_MID;
            }

            updateTaskStatus(taskId, ReindexTaskStatus.RUNNING, currentStep, totalDocs, processed);

            if (processed % 5000 == 0) {
                log.info("Task {}: Progress {}/{}", taskId, processed, totalDocs);
            }
        }
    }

    @Override
    public DataComparisonResponse compareWithDatabase() {
        try {
            long esCount = esOps.count(Query.findAll(), IndexCoordinates.of(getAlias()));
            ApiResponse<Long> response = messageServiceClient.getMessageCount();
            long dbCount = (response != null && response.data() != null) ? response.data() : 0;

            return DataComparisonResponse.builder()
                    .elasticsearchCount(esCount)
                    .databaseCount(dbCount)
                    .difference(Math.abs(esCount - dbCount))
                    .status(esCount == dbCount ? DataSyncStatus.IN_SYNC :
                            (esCount > dbCount ? DataSyncStatus.ES_AHEAD : DataSyncStatus.DB_AHEAD))
                    .build();
        } catch (Exception e) {
            return super.compareWithDatabase();
        }
    }
}
