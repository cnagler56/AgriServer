package com.home.Domain;

import java.util.ArrayList;
import java.util.List;

/**
 * One commodity (Corn, Soybeans, etc.) with its next N unexpired contracts.
 * The first contract in the list is the "front month" — what charts and headlines
 * usually quote.
 */
public class CommodityGroup {

	private String name;        // "Corn"
	private String unit;        // "¢/bu"
	private String exchange;    // "CBT" / "CME"
	private List<CommodityPrice> contracts = new ArrayList<>();

	public CommodityGroup() {}

	public CommodityGroup(String name, String unit, String exchange) {
		this.name = name;
		this.unit = unit;
		this.exchange = exchange;
	}

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getUnit() { return unit; }
	public void setUnit(String unit) { this.unit = unit; }

	public String getExchange() { return exchange; }
	public void setExchange(String exchange) { this.exchange = exchange; }

	public List<CommodityPrice> getContracts() { return contracts; }
	public void setContracts(List<CommodityPrice> contracts) { this.contracts = contracts; }
}
