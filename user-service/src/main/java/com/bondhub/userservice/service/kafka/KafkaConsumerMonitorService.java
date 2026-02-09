package com.bondhub.userservice.service.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerMonitorService {

    private final AdminClient kafkaAdminClient;

    public long getConsumerLag(String groupId, String topic) {
        try {
            if (!consumerGroupExists(groupId)) {
                log.warn("Consumer group '{}' does not exist yet", groupId);
                return 0;
            }

            Map<TopicPartition, OffsetAndMetadata> consumerOffsets =
                kafkaAdminClient.listConsumerGroupOffsets(groupId)
                    .partitionsToOffsetAndMetadata()
                    .get();

            if (consumerOffsets.isEmpty()) {
                log.debug("Consumer group '{}' has no committed offsets yet", groupId);
                return 0;
            }

            Map<TopicPartition, OffsetAndMetadata> filteredOffsets = consumerOffsets;
            if (topic != null) {
                filteredOffsets = consumerOffsets.entrySet().stream()
                    .filter(entry -> entry.getKey().topic().equals(topic))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            if (filteredOffsets.isEmpty()) {
                log.debug("No offsets found for topic '{}' in consumer group '{}'", topic, groupId);
                return 0;
            }

            Map<TopicPartition, OffsetSpec> requestLatestOffsets = new HashMap<>();
            for (TopicPartition tp : filteredOffsets.keySet()) {
                requestLatestOffsets.put(tp, OffsetSpec.latest());
            }

            ListOffsetsResult listOffsetsResult = kafkaAdminClient.listOffsets(requestLatestOffsets);
            Map<TopicPartition, Long> endOffsets = listOffsetsResult.all().get().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    entry -> entry.getValue().offset()
                ));

            long totalLag = 0;
            for (Map.Entry<TopicPartition, OffsetAndMetadata> entry : filteredOffsets.entrySet()) {
                TopicPartition tp = entry.getKey();
                long consumerOffset = entry.getValue().offset();
                long endOffset = endOffsets.getOrDefault(tp, 0L);
                long lag = Math.max(0, endOffset - consumerOffset);

                totalLag += lag;

                if (lag > 0) {
                    log.debug("Partition {}-{}: consumer={}, end={}, lag={}",
                        tp.topic(), tp.partition(), consumerOffset, endOffset, lag);
                }
            }

            return totalLag;

        } catch (ExecutionException e) {
            log.error("Failed to get consumer lag for group '{}': {}", groupId, e.getMessage(), e);
            throw new RuntimeException("Failed to get consumer lag", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while getting consumer lag", e);
        }
    }

    private boolean consumerGroupExists(String groupId) {
        try {
            Collection<ConsumerGroupListing> groups = kafkaAdminClient.listConsumerGroups()
                .all()
                .get();

            return groups.stream()
                .anyMatch(group -> group.groupId().equals(groupId));

        } catch (Exception e) {
            log.warn("Failed to check if consumer group exists: {}", e.getMessage());
            return false;
        }
    }

    public boolean waitForLagZero(String groupId, String topic, int maxWaitSeconds) {
        log.info("⏳ Waiting for consumer group '{}' to process topic '{}'...", groupId, topic);

        long startTime = System.currentTimeMillis();
        int checkIntervalMs = 2000;
        int waitedSeconds = 0;

        while (waitedSeconds < maxWaitSeconds) {
            try {
                long lag = getConsumerLag(groupId, topic);

                if (lag == 0) {
                    log.info("✅ Consumer lag is 0! All messages processed (waited {} seconds)", waitedSeconds);
                    return true;
                }

                log.info("Consumer lag: {} messages remaining... (waited {}/{} seconds)",
                    lag, waitedSeconds, maxWaitSeconds);

                Thread.sleep(checkIntervalMs);
                waitedSeconds = (int) ((System.currentTimeMillis() - startTime) / 1000);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Interrupted while waiting for consumer lag");
                return false;
            } catch (Exception e) {
                log.error("Error while checking consumer lag: {}", e.getMessage());
            }
        }

        long finalLag = getConsumerLag(groupId, topic);
        log.error("⏰ Timeout! Consumer lag still: {} messages after {} seconds", finalLag, maxWaitSeconds);
        return false;
    }
}
