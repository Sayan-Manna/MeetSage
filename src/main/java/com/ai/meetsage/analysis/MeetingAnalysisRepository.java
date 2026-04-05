package com.ai.meetsage.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for MeetingAnalysis entities.
 */
@Repository
public interface MeetingAnalysisRepository extends JpaRepository<MeetingAnalysis, UUID> {

    Optional<MeetingAnalysis> findByMeetingId(UUID meetingId);
}
