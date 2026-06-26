package com.home.Domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * A raw WASDE machine-readable CSV uploaded by an admin, stored in the DB so it
 * survives container redeploys (Railway's filesystem is ephemeral). The supply &
 * demand ingest reads these alongside any bundled CSVs.
 */
@Entity
@Table(name = "wasde_file")
public class WasdeFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Source filename, e.g. "oce-wasde-report-data-2026-07-V2.csv". Unique. */
    @Column(unique = true, length = 255)
    private String filename;

    /** YYYYMM snapshot key parsed from the file (for display + de-duping). */
    private Integer monthKey;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String content;

    private LocalDateTime uploadedAt;

    public Long getId() { return id; }
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    public Integer getMonthKey() { return monthKey; }
    public void setMonthKey(Integer monthKey) { this.monthKey = monthKey; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
