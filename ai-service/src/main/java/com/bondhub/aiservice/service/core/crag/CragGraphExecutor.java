package com.bondhub.aiservice.service.core.crag;

import com.bondhub.aiservice.model.CragState;
import com.bondhub.aiservice.model.MongoGraphCheckpoint;
import com.bondhub.aiservice.model.enums.AiProcessingStatus;
import com.bondhub.aiservice.service.core.state.MongoGraphCheckpointer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class CragGraphExecutor {

    private final CragNodes nodes;
    private final MongoGraphCheckpointer checkpointer;
    private final CragGraphConfig graphConfig;

    public CragState execute(CragState initialState) {
        CragState state = initialState;

        emitStatus(state.getConvId(), AiProcessingStatus.ANALYZING_INTENT);
        state = applyUpdates(state, nodes.rewriteNode(state));
        state = applyUpdates(state, nodes.analyzeNode(state));

        String analyzeNext = graphConfig.nextAfterAnalyze(state.getRoute());
        if (CragGraphConfig.NODE_CLARIFY.equals(analyzeNext)) {
            state = applyUpdates(state, nodes.clarifyNode(state));
            checkpointer.saveWaitingContext(state);
            return state;
        }

        if (CragGraphConfig.NODE_RETRIEVE.equals(analyzeNext)) {
            emitStatus(state.getConvId(), AiProcessingStatus.RETRIEVING_VECTOR);
            state = applyUpdates(state, nodes.retrieveNode(state));

            emitStatus(state.getConvId(), AiProcessingStatus.GRADING_DATA);
            state = applyUpdates(state, nodes.gradeNode(state));

            String gradeNext = graphConfig.nextAfterGrade(state.getGrade(), state.getRetryCount());
            if (CragGraphConfig.NODE_WEB_SEARCH.equals(gradeNext)) {
                emitStatus(state.getConvId(), AiProcessingStatus.WEB_SEARCHING);
                state = applyUpdates(state, nodes.webSearchNode(state));
            } else if (CragGraphConfig.NODE_MARK_LOW_CONFIDENCE.equals(gradeNext)) {
                state = applyUpdates(state, nodes.markLowConfidenceNode(state));
            }
        }

        emitStatus(state.getConvId(), AiProcessingStatus.GENERATING_ANSWER);
        state = applyUpdates(state, nodes.generateNode(state));
        checkpointer.clear(state.getConvId());
        return state;
    }

    public CragState hydrateForResume(CragState state) {
        MongoGraphCheckpoint checkpoint = checkpointer.load(state.getConvId());
        if (checkpoint == null) {
            return state;
        }

        state.setOriginalQuery(checkpoint.getOriginalQuery());
        state.setMissingFieldInfo(checkpoint.getMissingFieldInfo());
        state.setRetryCount(checkpoint.getRetryCount());
        state.setResumedFromCheckpoint(true);
        return state;
    }

    private static void emitStatus(String convId, AiProcessingStatus status) {
        Sinks.Many<String> sink = GraphRuntimeRegistry.getSink(convId);
        if (sink != null) {
            sink.tryEmitNext("{\"type\":\"STATUS\",\"content\":\"" + status.name() + "\"}");
        }
    }

    @SuppressWarnings("unchecked")
    private CragState applyUpdates(CragState state, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return state;
        }

        updates.forEach((key, value) -> {
            switch (key) {
                case "rewrittenQuery" -> state.setRewrittenQuery((String) value);
                case "route" -> state.setRoute((String) value);
                case "missingFieldInfo" -> state.setMissingFieldInfo((String) value);
                case "internalContext" -> state.setInternalContext((String) value);
                case "webContext" -> state.setWebContext((String) value);
                case "context" -> state.setContext((String) value);
                case "grade" -> state.setGrade((String) value);
                case "finalAnswer" -> state.setFinalAnswer((String) value);
                case "suggestedQuestions" -> state.setSuggestedQuestions((List<String>) value);
                case "retryCount" -> state.setRetryCount((Integer) value);
                case "lowConfidenceContext" -> state.setLowConfidenceContext((Boolean) value);
                case "qualityNote" -> state.setQualityNote((String) value);
                case "statusTrail" -> state.setStatusTrail((List<String>) value);
                case "lastError" -> state.setLastError((String) value);
                default -> log.debug("[Graph] Ignored update key={}", key);
            }
        });

        return state;
    }
}
