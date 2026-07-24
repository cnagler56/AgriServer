package com.home.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.AnalystSubscriber;

@Repository
public interface AnalystSubscriberRepository extends JpaRepository<AnalystSubscriber, Long> {

	/** Any provider granting this email access → read entitlement. */
	boolean existsByEmailIgnoreCase(String email);

	List<AnalystSubscriber> findByAnalystUserIdOrderByAddedAtDesc(Long analystUserId);

	Optional<AnalystSubscriber> findByAnalystUserIdAndEmailIgnoreCase(Long analystUserId, String email);
}
