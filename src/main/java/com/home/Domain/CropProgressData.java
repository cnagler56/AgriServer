package com.home.Domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One row of NASS Crop Progress data (statisticcat_desc = PROGRESS).
 * Not a JPA entity — these are live, weekly, and not persisted.
 *
 * NASS Crop Progress example:
 *   short_desc:                "CORN - PROGRESS, MEASURED IN PCT PLANTED"
 *   unit_desc:                 "PCT PLANTED"
 *   reference_period_desc:     "WEEK ENDING MAY 12"
 *   week_ending:               "2024-05-12"
 *   state_name:                "IOWA"
 *   Value:                     "75"   (percent)
 */
public class CropProgressData {

    @JsonProperty
    private String commodity;

    @JsonProperty
    private String state;

    @JsonProperty
    private Integer year;

    /** e.g. "PCT PLANTED", "PCT EMERGED", "PCT HARVESTED" — identifies the growth stage. */
    @JsonProperty
    private String unit;

    /** ISO date string e.g. "2024-05-12" — the Sunday this report covers. */
    @JsonProperty
    private String weekEnding;

    /** Raw percent value as reported by NASS (string to preserve formatting). */
    @JsonProperty
    private String value;

    @JsonProperty
    private String shortDesc;

    public String getCommodity() { return commodity; }
    public void setCommodity(String commodity) { this.commodity = commodity; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getWeekEnding() { return weekEnding; }
    public void setWeekEnding(String weekEnding) { this.weekEnding = weekEnding; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getShortDesc() { return shortDesc; }
    public void setShortDesc(String shortDesc) { this.shortDesc = shortDesc; }
}
