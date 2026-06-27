package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.Announcement;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

	/** The single announcement row we maintain (latest by id), for admin edit/upsert. */
	Optional<Announcement> findTopByOrderByIdDesc();

	/** The active announcement to show publicly, if any. */
	Optional<Announcement> findTopByActiveTrueOrderByUpdatedAtDesc();
}
