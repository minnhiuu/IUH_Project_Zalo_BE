package com.bondhub.userservice.controller.admin;

import com.bondhub.common.config.kafka.KafkaTopicProperties;
import com.bondhub.common.dto.ApiResponse;
import com.bondhub.userservice.service.kafka.KafkaConsumerMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/users/kafka")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class KafkaMonitoringController {

    private final KafkaConsumerMonitorService kafkaMonitorService;
    private final KafkaTopicProperties kafkaTopicProperties;

    @GetMapping("/consumer-lag")
    public ApiResponse<Map<String, Object>> getConsumerLag() {
        String consumerGroup = "user-search-indexer-group";
        String indexRequestedTopic = kafkaTopicProperties.getUserEvents().getIndexRequested();
        String indexDeletedTopic = kafkaTopicProperties.getUserEvents().getIndexDeleted();

        long indexRequestedLag = kafkaMonitorService.getConsumerLag(consumerGroup, indexRequestedTopic);
        long indexDeletedLag = kafkaMonitorService.getConsumerLag(consumerGroup, indexDeletedTopic);
        long totalLag = kafkaMonitorService.getConsumerLag(consumerGroup, null);

        Map<String, Object> result = new HashMap<>();
        result.put("consumerGroup", consumerGroup);
        result.put("topics", Map.of(
            indexRequestedTopic, indexRequestedLag,
            indexDeletedTopic, indexDeletedLag
        ));
        result.put("totalLag", totalLag);
        result.put("status", totalLag == 0 ? "UP_TO_DATE" : "PROCESSING");

        return ApiResponse.success(result);
    }

    @GetMapping("/consumer-lag/{topic}")
    public ApiResponse<Map<String, Object>> getConsumerLagByTopic(
            @PathVariable String topic,
            @RequestParam(defaultValue = "user-search-indexer-group") String groupId) {

        long lag = kafkaMonitorService.getConsumerLag(groupId, topic);

        Map<String, Object> result = new HashMap<>();
        result.put("consumerGroup", groupId);
        result.put("topic", topic);
        result.put("lag", lag);
        result.put("status", lag == 0 ? "UP_TO_DATE" : "PROCESSING");

        return ApiResponse.success(result);
    }
}
