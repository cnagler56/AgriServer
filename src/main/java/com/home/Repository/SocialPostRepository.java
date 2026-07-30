package com.home.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.home.Domain.SocialPost;

public interface SocialPostRepository extends JpaRepository<SocialPost, Long> {

	/** Most recent actually-emitted post (POSTED or DRYRUN) — drives page rotation. */
	Optional<SocialPost> findTopByStatusInOrderByCreatedAtDesc(Collection<String> statuses);

	/** Recent activity for the admin log. */
	List<SocialPost> findTop20ByOrderByCreatedAtDesc();
}
