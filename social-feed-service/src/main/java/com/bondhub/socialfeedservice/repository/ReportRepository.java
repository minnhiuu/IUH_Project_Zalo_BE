package com.bondhub.socialfeedservice.repository;

import com.bondhub.socialfeedservice.model.Report;
import com.bondhub.socialfeedservice.model.enums.ReportStatus;
import com.bondhub.socialfeedservice.model.enums.TargetType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends MongoRepository<Report, String> {

    Optional<Report> findByReporterIdAndTargetIdAndTargetTypeAndStatus(
            String reporterId, String targetId, TargetType targetType, ReportStatus status);

    Page<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    Page<Report> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByTargetIdAndTargetTypeAndStatus(String targetId, TargetType targetType, ReportStatus status);

    List<Report> findByTargetIdAndTargetType(String targetId, TargetType targetType);
}
