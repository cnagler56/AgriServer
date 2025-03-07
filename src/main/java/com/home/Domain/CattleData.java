package com.home.Domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@Entity
@Table(name="cattle")
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CattleData {

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


    public String getValue() {
        return inventory;
    }
    public String getStateName() {
    	return stateName;
    }
    
    @Override
    public String toString() {
        return "CattleData{" +
                "id=" + id +
                ", animal='" + animal + '\'' +
                ", loadTime=" + loadTime +
                ", stateName='" + stateName + '\'' +
                ", inventory='" + inventory + '\'' +
                '}';
    }
}