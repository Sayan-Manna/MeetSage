package com.ai.meetsage.meeting;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Spring Data JPA repository for Meeting entities.
 */
@Repository
public interface MeetingRepository extends JpaRepository<Meeting, UUID> {

    Page<Meeting> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
