package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.home.Domain.SiteSetting;

@Repository
public interface SiteSettingRepository extends JpaRepository<SiteSetting, Long> {
	Optional<SiteSetting> findBySettingKey(String settingKey);
}
