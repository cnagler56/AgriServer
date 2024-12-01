package com.home.Domain;

import jakarta.persistence.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name="hogs")
@JsonIgnoreProperties(ignoreUnknown = true)
public class HogsData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @JsonProperty("short_desc")
    private String animal;


	@JsonProperty("load_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSS")
    private LocalDateTime loadTime;

    @JsonProperty("location_desc")
    private String stateName;

    @JsonProperty("Value")
    @Column(name="inventory", nullable = true)
    private String inventory;
    
}