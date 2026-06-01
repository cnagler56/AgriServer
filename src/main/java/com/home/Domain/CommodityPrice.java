package com.home.Domain;

/**
 * A single commodity futures snapshot returned from /prices.
 * Not persisted — just a DTO from Yahoo Finance.
 */
public class CommodityPrice {

	private String symbol;          // e.g. "ZCN26.CBT"
	private String name;            // e.g. "Corn"
	private String unit;            // e.g. "¢/bu" — what the price is denominated in
	private String expiration;      // human-friendly e.g. "Jul 2026"
	private Integer expirationKey;  // sortable YYYYMM e.g. 202607
	private Double last;            // last regular-market price
	private Double previousClose;
	private Double change;          // last - previousClose
	private Double changePercent;   // change / previousClose * 100
	private Long asOf;              // unix seconds from Yahoo
	private String error;           // populated when a single symbol fetch failed

	public CommodityPrice() {}

	public CommodityPrice(String symbol, String name, String unit) {
		this.symbol = symbol;
		this.name = name;
		this.unit = unit;
	}

	public String getExpiration() { return expiration; }
	public void setExpiration(String expiration) { this.expiration = expiration; }

	public Integer getExpirationKey() { return expirationKey; }
	public void setExpirationKey(Integer expirationKey) { this.expirationKey = expirationKey; }

	public String getSymbol() { return symbol; }
	public void setSymbol(String symbol) { this.symbol = symbol; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getUnit() { return unit; }
	public void setUnit(String unit) { this.unit = unit; }

	public Double getLast() { return last; }
	public void setLast(Double last) { this.last = last; }

	public Double getPreviousClose() { return previousClose; }
	public void setPreviousClose(Double previousClose) { this.previousClose = previousClose; }

	public Double getChange() { return change; }
	public void setChange(Double change) { this.change = change; }

	public Double getChangePercent() { return changePercent; }
	public void setChangePercent(Double changePercent) { this.changePercent = changePercent; }

	public Long getAsOf() { return asOf; }
	public void setAsOf(Long asOf) { this.asOf = asOf; }

	public String getError() { return error; }
	public void setError(String error) { this.error = error; }
}
