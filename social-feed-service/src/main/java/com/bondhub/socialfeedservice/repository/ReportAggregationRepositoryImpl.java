package com.bondhub.socialfeedservice.repository;

import com.bondhub.common.dto.PageResponse;
import com.bondhub.socialfeedservice.dto.response.post.AuthorInfo;
import com.bondhub.socialfeedservice.dto.response.report.ContentReportSummary;
import com.bondhub.socialfeedservice.dto.response.report.ReportDetailResponse;
import com.bondhub.socialfeedservice.model.enums.AdminAction;
import com.bondhub.socialfeedservice.model.enums.ReportReason;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Repository
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ReportAggregationRepositoryImpl implements ReportAggregationRepository {

    MongoTemplate mongoTemplate;


    @Override
    public PageResponse<List<ContentReportSummary>> findGroupedReports(
            ReportStatus status, int page, int size) {

        long totalItems = countGroupedReports(status);
        int totalPages = (int) Math.ceil((double) totalItems / size);

        List<AggregationOperation> operations = new ArrayList<>();

        // Stage 1: Group by targetId + targetType, accumulate per-status counts
        operations.add(context -> new Document("$group", new Document()
                .append("_id", new Document()
                        .append("targetId", "$targetId")
                        .append("targetType", "$targetType"))
                .append("totalReports", new Document("$sum", 1))
                .append("pendingCount", new Document("$sum", new Document("$cond", List.of(
                        new Document("$eq", List.of("$status", "PENDING")), 1, 0))))
                .append("resolvedCount", new Document("$sum", new Document("$cond", List.of(
                        new Document("$eq", List.of("$status", "RESOLVED")), 1, 0))))
                .append("dismissedCount", new Document("$sum", new Document("$cond", List.of(
                        new Document("$eq", List.of("$status", "DISMISSED")), 1, 0))))
                .append("reasons", new Document("$addToSet", "$reason"))
                .append("latestReportAt", new Document("$max", "$createdAt"))
        ));

        // Stage 2: Flatten _id and derive overallStatus
        operations.add(context -> new Document("$addFields", new Document()
                .append("targetId", "$_id.targetId")
                .append("targetType", "$_id.targetType")
                .append("overallStatus", new Document("$switch", new Document()
                        .append("branches", List.of(
                                new Document()
                                        .append("case", new Document("$gt", List.of("$pendingCount", 0)))
                                        .append("then", "PENDING"),
                                new Document()
                                        .append("case", new Document("$gt", List.of("$resolvedCount", 0)))
                                        .append("then", "RESOLVED")
                        ))
                        .append("default", "DISMISSED")
                ))
        ));

        // Stage 3: Filter by overallStatus if provided
        if (status != null) {
            operations.add(Aggregation.match(Criteria.where("overallStatus").is(status.name())));
        }

        // Stage 4: Sort by latest report descending
        operations.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "latestReportAt")));

        // Stage 5: Pagination
        operations.add(Aggregation.skip((long) page * size));
        operations.add(Aggregation.limit(size));

        // Stage 6: Lookup post content
        operations.add(context -> new Document("$lookup", new Document()
                .append("from", "posts")
                .append("let", new Document("tid", new Document("$toObjectId", "$targetId")))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr",
                                new Document("$eq", List.of("$_id", "$$tid"))
                        ))
                ))
                .append("as", "postDetails")
        ));

        // Stage 7: Lookup comment content
        operations.add(context -> new Document("$lookup", new Document()
                .append("from", "comments")
                .append("let", new Document("tid", new Document("$toObjectId", "$targetId")))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr",
                                new Document("$eq", List.of("$_id", "$$tid"))
                        ))
                ))
                .append("as", "commentDetails")
        ));

        // Stage 8: Merge content doc based on targetType
        operations.add(context -> new Document("$addFields", new Document()
                .append("contentDoc", new Document("$cond", List.of(
                        new Document("$eq", List.of("$targetType", "POST")),
                        new Document("$arrayElemAt", List.of("$postDetails", 0)),
                        new Document("$arrayElemAt", List.of("$commentDetails", 0))
                )))
        ));

        // Stage 9: Extract contentAuthorId from content doc
        operations.add(context -> new Document("$addFields", new Document()
                .append("contentAuthorId", "$contentDoc.authorId")
        ));

        // Stage 10: Lookup author profile from user_summaries
        operations.add(context -> new Document("$lookup", new Document()
                .append("from", "user_summaries")
                .append("localField", "contentAuthorId")
                .append("foreignField", "_id")
                .append("as", "authorDetails")
        ));

        // Stage 11: Extract first author doc
        operations.add(context -> new Document("$addFields", new Document()
                .append("contentAuthorDoc", new Document("$arrayElemAt", List.of("$authorDetails", 0)))
        ));

        Aggregation aggregation = Aggregation.newAggregation(operations);
        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, "reports", Document.class);

        List<ContentReportSummary> responses = results.getMappedResults().stream()
                .map(this::mapToContentReportSummary)
                .toList();

        return PageResponse.<List<ContentReportSummary>>builder()
                .data(responses)
                .page(page)
                .totalPages(totalPages)
                .limit(size)
                .totalItems(totalItems)
                .build();
    }

    @Override
    public List<ReportDetailResponse> findReportsByTarget(String targetId, TargetType targetType) {
        List<AggregationOperation> operations = new ArrayList<>();

        // Pre-filter by targetId and targetType
        operations.add(Aggregation.match(
                Criteria.where("targetId").is(targetId)
                        .and("targetType").is(targetType.name())));

        // Sort by createdAt descending
        operations.add(Aggregation.sort(Sort.by(Sort.Direction.DESC, "createdAt")));

        // Lookup post content
        operations.add(context -> new Document("$lookup", new Document()
                .append("from", "posts")
                .append("let", new Document("tid", new Document("$toObjectId", "$targetId")))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr",
                                new Document("$eq", List.of("$_id", "$$tid"))
                        ))
                ))
                .append("as", "postDetails")
        ));

        // Lookup comment content
        operations.add(context -> new Document("$lookup", new Document()
                .append("from", "comments")
                .append("let", new Document("tid", new Document("$toObjectId", "$targetId")))
                .append("pipeline", List.of(
                        new Document("$match", new Document("$expr",
                                new Document("$eq", List.of("$_id", "$$tid"))
                        ))
                ))
                .append("as", "commentDetails")
        ));

        // Lookup reporter info from user_summaries
        operations.add(context -> new Document("$lookup", new Document()
                .append("from", "user_summaries")
                .append("localField", "reporterId")
                .append("foreignField", "_id")
                .append("as", "reporterDetails")
        ));

        // Merge content and reporter docs
        operations.add(context -> new Document("$addFields", new Document()
                .append("contentDoc", new Document("$cond", List.of(
                        new Document("$eq", List.of("$targetType", "POST")),
                        new Document("$arrayElemAt", List.of("$postDetails", 0)),
                        new Document("$arrayElemAt", List.of("$commentDetails", 0))
                )))
                .append("reporterDoc", new Document("$arrayElemAt", List.of("$reporterDetails", 0)))
        ));

        Aggregation aggregation = Aggregation.newAggregation(operations);
        AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation, "reports", Document.class);

        return results.getMappedResults().stream()
                .map(this::mapToReportDetailResponse)
                .toList();
    }


    private long countGroupedReports(ReportStatus status) {
        List<AggregationOperation> ops = new ArrayList<>();

        ops.add(context -> new Document("$group", new Document()
                .append("_id", new Document()
                        .append("targetId", "$targetId")
                        .append("targetType", "$targetType"))
                .append("pendingCount", new Document("$sum", new Document("$cond", List.of(
                        new Document("$eq", List.of("$status", "PENDING")), 1, 0))))
                .append("resolvedCount", new Document("$sum", new Document("$cond", List.of(
                        new Document("$eq", List.of("$status", "RESOLVED")), 1, 0))))
        ));

        ops.add(context -> new Document("$addFields", new Document()
                .append("overallStatus", new Document("$switch", new Document()
                        .append("branches", List.of(
                                new Document()
                                        .append("case", new Document("$gt", List.of("$pendingCount", 0)))
                                        .append("then", "PENDING"),
                                new Document()
                                        .append("case", new Document("$gt", List.of("$resolvedCount", 0)))
                                        .append("then", "RESOLVED")
                        ))
                        .append("default", "DISMISSED")
                ))
        ));

        if (status != null) {
            ops.add(Aggregation.match(Criteria.where("overallStatus").is(status.name())));
        }

        ops.add(context -> new Document("$count", "total"));

        AggregationResults<Document> result = mongoTemplate.aggregate(
                Aggregation.newAggregation(ops), "reports", Document.class);

        List<Document> docs = result.getMappedResults();
        if (docs.isEmpty()) return 0;
        return ((Number) docs.get(0).get("total")).longValue();
    }


    private ContentReportSummary mapToContentReportSummary(Document doc) {
        Document contentDoc = doc.get("contentDoc", Document.class);
        Document contentAuthorDoc = doc.get("contentAuthorDoc", Document.class);

        String contentText = null;
        String contentAuthorId = doc.getString("contentAuthorId");
        List<String> contentMediaUrls = null;

        if (contentDoc != null) {
            if (contentAuthorId == null) {
                contentAuthorId = contentDoc.getString("authorId");
            }
            // Post: content sub-doc with caption; Comment: direct content string
            Document postContent = contentDoc.get("content", Document.class);
            if (postContent != null) {
                contentText = postContent.getString("caption");
            }
            if (contentText == null) {
                contentText = contentDoc.getString("content");
            }
            List<Document> mediaDocs = contentDoc.getList("media", Document.class);
            if (mediaDocs != null && !mediaDocs.isEmpty()) {
                contentMediaUrls = mediaDocs.stream()
                        .map(m -> m.getString("url"))
                        .toList();
            }
        }

        AuthorInfo contentAuthorInfo = null;
        if (contentAuthorDoc != null) {
            contentAuthorInfo = AuthorInfo.builder()
                    .id(contentAuthorDoc.getString("_id"))
                    .fullName(contentAuthorDoc.getString("fullName"))
                    .avatar(contentAuthorDoc.getString("avatar"))
                    .build();
        }

        List<?> rawReasons = doc.getList("reasons", Object.class);
        List<ReportReason> reasons = rawReasons != null
                ? rawReasons.stream()
                        .filter(r -> r != null)
                        .map(r -> ReportReason.valueOf(r.toString()))
                        .toList()
                : List.of();

        return ContentReportSummary.builder()
                .targetId(doc.getString("targetId"))
                .targetType(TargetType.valueOf(doc.getString("targetType")))
                .totalReports(doc.getInteger("totalReports", 0))
                .pendingCount(doc.getInteger("pendingCount", 0))
                .resolvedCount(doc.getInteger("resolvedCount", 0))
                .dismissedCount(doc.getInteger("dismissedCount", 0))
                .reasons(reasons)
                .latestReportAt(toLocalDateTime(doc.getDate("latestReportAt")))
                .contentText(contentText)
                .contentMediaUrls(contentMediaUrls)
                .contentAuthorId(contentAuthorId)
                .contentAuthorInfo(contentAuthorInfo)
                .overallStatus(ReportStatus.valueOf(doc.getString("overallStatus")))
                .build();
    }

    private ReportDetailResponse mapToReportDetailResponse(Document doc) {
        Document contentDoc = doc.get("contentDoc", Document.class);
        Document reporterDoc = doc.get("reporterDoc", Document.class);

        String contentText = null;
        String contentAuthorId = null;
        List<String> contentMediaUrls = null;

        if (contentDoc != null) {
            contentAuthorId = contentDoc.getString("authorId");
            Document postContent = contentDoc.get("content", Document.class);
            if (postContent != null) {
                contentText = postContent.getString("caption");
            }
            if (contentText == null) {
                contentText = contentDoc.getString("content");
            }
            List<Document> mediaDocs = contentDoc.getList("media", Document.class);
            if (mediaDocs != null && !mediaDocs.isEmpty()) {
                contentMediaUrls = mediaDocs.stream()
                        .map(m -> m.getString("url"))
                        .toList();
            }
        }

        AuthorInfo reporterInfo = null;
        if (reporterDoc != null) {
            reporterInfo = AuthorInfo.builder()
                    .id(reporterDoc.getString("_id"))
                    .fullName(reporterDoc.getString("fullName"))
                    .avatar(reporterDoc.getString("avatar"))
                    .build();
        }

        AuthorInfo contentAuthorInfo = null;
        if (contentAuthorId != null) {
            contentAuthorInfo = AuthorInfo.builder()
                    .id(contentAuthorId)
                    .build();
        }

        ObjectId objectId = doc.getObjectId("_id");
        String adminActionStr = doc.getString("adminAction");
        AdminAction adminAction = adminActionStr != null ? AdminAction.valueOf(adminActionStr) : null;

        return ReportDetailResponse.builder()
                .id(objectId != null ? objectId.toHexString() : null)
                .reporterId(doc.getString("reporterId"))
                .targetId(doc.getString("targetId"))
                .targetType(TargetType.valueOf(doc.getString("targetType")))
                .reason(ReportReason.valueOf(doc.getString("reason")))
                .details(doc.getString("details"))
                .status(ReportStatus.valueOf(doc.getString("status")))
                .adminId(doc.getString("adminId"))
                .adminNote(doc.getString("adminNote"))
                .adminAction(adminAction)
                .createdAt(toLocalDateTime(doc.getDate("createdAt")))
                .updatedAt(toLocalDateTime(doc.getDate("lastModifiedAt")))
                .contentText(contentText)
                .contentMediaUrls(contentMediaUrls)
                .contentAuthorId(contentAuthorId)
                .contentAuthorInfo(contentAuthorInfo)
                .reporterInfo(reporterInfo)
                .build();
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) return null;
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }


    @Deprecated
    @SuppressWarnings("unused")
    public PageResponse<List<ReportDetailResponse>> findReportsWithContentDetails(
            ReportStatus status, int page, int size) {

        throw new UnsupportedOperationException("Use findGroupedReports instead");
    }
}
