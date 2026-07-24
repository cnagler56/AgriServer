package com.home.Service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.home.Domain.AnalysisPost;
import com.home.Domain.AnalystSubscriber;
import com.home.Domain.Role;
import com.home.Domain.User;
import com.home.Repository.AnalysisPostRepository;
import com.home.Repository.AnalystSubscriberRepository;

/**
 * The gated market-analysis feature.
 *
 * Two permissions, kept separate:
 *   - PUBLISH: an ANALYST (or ADMIN) authors posts and manages their own
 *     subscriber list.
 *   - READ: an ANALYST/ADMIN, or any signed-in user whose email a provider has
 *     added as a subscriber.
 *
 * The read gate is enforced here (in getFeed), not just in the UI — the tab
 * being hidden is cosmetic; this is the actual access control.
 */
@Service
public class AnalysisService {

	private final AnalysisPostRepository postRepo;
	private final AnalystSubscriberRepository subRepo;

	public AnalysisService(AnalysisPostRepository postRepo, AnalystSubscriberRepository subRepo) {
		this.postRepo = postRepo;
		this.subRepo = subRepo;
	}

	/* ── entitlement ────────────────────────────────────────────────────── */

	public boolean canPublish(User u) {
		return u != null && (u.getRoles() == Role.ANALYST || u.getRoles() == Role.ADMIN);
	}

	public boolean canRead(User u) {
		if (u == null) return false;
		if (canPublish(u)) return true;
		return u.getEmail() != null && subRepo.existsByEmailIgnoreCase(u.getEmail().trim());
	}

	public record Access(boolean canRead, boolean canPublish) {}

	public Access access(User u) {
		return new Access(canRead(u), canPublish(u));
	}

	/* ── reader feed ────────────────────────────────────────────────────── */

	/** Published posts for an entitled reader. Caller must confirm canRead first. */
	public List<AnalysisPost> feed() {
		return postRepo.findByPublishedTrueOrderByPublishedAtDesc();
	}

	/* ── author: posts ──────────────────────────────────────────────────── */

	public List<AnalysisPost> myPosts(User author) {
		return postRepo.findByAuthorUserIdOrderByUpdatedAtDesc(author.getUserId());
	}

	@Transactional
	public AnalysisPost savePost(User author, Long id, String title, String body, boolean publish) {
		LocalDateTime now = LocalDateTime.now();
		AnalysisPost p;
		if (id != null) {
			p = postRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("Post not found"));
			if (!owns(author, p)) throw new SecurityException("Not your post");
		} else {
			p = new AnalysisPost();
			p.setAuthorUserId(author.getUserId());
			p.setAuthorName(author.getName());
		}
		p.setTitle(title == null ? "" : title.trim());
		p.setBody(body == null ? "" : body);
		boolean wasPublished = p.isPublished();
		p.setPublished(publish);
		if (publish && !wasPublished) p.setPublishedAt(now);   // stamp first publish
		p.setUpdatedAt(now);
		return postRepo.save(p);
	}

	@Transactional
	public void deletePost(User author, Long id) {
		AnalysisPost p = postRepo.findById(id).orElse(null);
		if (p == null) return;
		if (!owns(author, p)) throw new SecurityException("Not your post");
		postRepo.delete(p);
	}

	private boolean owns(User u, AnalysisPost p) {
		return u.getRoles() == Role.ADMIN || u.getUserId().equals(p.getAuthorUserId());
	}

	/* ── author: subscribers ────────────────────────────────────────────── */

	public List<AnalystSubscriber> mySubscribers(User author) {
		return subRepo.findByAnalystUserIdOrderByAddedAtDesc(author.getUserId());
	}

	@Transactional
	public AnalystSubscriber addSubscriber(User author, String email, String note) {
		if (email == null || email.isBlank()) throw new IllegalArgumentException("Email required");
		String norm = email.trim().toLowerCase();
		AnalystSubscriber s = subRepo
			.findByAnalystUserIdAndEmailIgnoreCase(author.getUserId(), norm)
			.orElseGet(AnalystSubscriber::new);
		s.setAnalystUserId(author.getUserId());
		s.setEmail(norm);
		if (note != null && !note.isBlank()) s.setNote(note.trim());
		if (s.getAddedAt() == null) s.setAddedAt(LocalDateTime.now());
		return subRepo.save(s);
	}

	@Transactional
	public void removeSubscriber(User author, Long id) {
		AnalystSubscriber s = subRepo.findById(id).orElse(null);
		if (s == null) return;
		if (!(author.getRoles() == Role.ADMIN || author.getUserId().equals(s.getAnalystUserId())))
			throw new SecurityException("Not your subscriber");
		subRepo.delete(s);
	}
}
