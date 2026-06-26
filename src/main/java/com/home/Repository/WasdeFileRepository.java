package com.home.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.home.Domain.WasdeFile;

public interface WasdeFileRepository extends JpaRepository<WasdeFile, Long> {
    Optional<WasdeFile> findByFilename(String filename);
}
