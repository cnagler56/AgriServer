package com.home.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.AnalysisPost;

@Repository
public interface AnalysisPostRepository extends JpaRepository<AnalysisPost, Long> {

	/** Reader feed: published posts, newest first. */
	List<AnalysisPost> findByPublishedTrueOrderByPublishedAtDesc();

	/** An author's own posts (drafts included), newest activity first. */
	List<AnalysisPost> findByAuthorUserIdOrderByUpdatedAtDesc(Long authorUserId);
}
