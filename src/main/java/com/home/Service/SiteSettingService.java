package com.home.Service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.home.Domain.SiteSetting;
import com.home.Repository.SiteSettingRepository;

/** Read/write access to admin-editable key/value site settings. */
@Service
public class SiteSettingService {

	private final SiteSettingRepository repo;

	public SiteSettingService(SiteSettingRepository repo) {
		this.repo = repo;
	}

	/** The stored value for {@code key}, or {@code dflt} when unset/blank. */
	public String get(String key, String dflt) {
		return repo.findBySettingKey(key)
			.map(SiteSetting::getSettingValue)
			.filter(v -> v != null && !v.isBlank())
			.orElse(dflt);
	}

	@Transactional
	public String set(String key, String value) {
		SiteSetting s = repo.findBySettingKey(key).orElseGet(() -> {
			SiteSetting fresh = new SiteSetting();
			fresh.setSettingKey(key);
			return fresh;
		});
		s.setSettingValue(value);
		s.setUpdatedAt(LocalDateTime.now());
		repo.save(s);
		return value;
	}
}
