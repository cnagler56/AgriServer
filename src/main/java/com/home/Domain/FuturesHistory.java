package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Cached daily OHLC history for one commodity's front-month futures contract.
 * The bar series is stored as a JSON string (compact, ~1 year of days) so the
 * chart survives redeploys and a weekly Friday refresh is all that's needed.
 */
@Entity
@Table(name = "futures_history", uniqueConstraints = @UniqueConstraint(columnNames = {"commodity"}))
public class FuturesHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonProperty
	private Long id;

	/** Site code: CORN / SOYBEANS / WHEAT / … */
	@JsonProperty
	private String commodity;

	/** Yahoo symbol the bars came from, e.g. "ZC=F". */
	@JsonProperty
	private String symbol;

	/** Quote currency reported by Yahoo (USX = cents, USD = dollars). */
	@JsonProperty
	private String currency;

	/** JSON array of bars: [{"t":epochSec,"o":..,"h":..,"l":..,"c":..,"v":..}, …]. */
	@Lob
	@JsonProperty
	private String barsJson;

	@JsonProperty
	private LocalDateTime updatedAt;

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getCommodity() { return commodity; }
	public void setCommodity(String commodity) { this.commodity = commodity; }

	public String getSymbol() { return symbol; }
	public void setSymbol(String symbol) { this.symbol = symbol; }

	public String getCurrency() { return currency; }
	public void setCurrency(String currency) { this.currency = currency; }

	public String getBarsJson() { return barsJson; }
	public void setBarsJson(String barsJson) { this.barsJson = barsJson; }

	public LocalDateTime getUpdatedAt() { return updatedAt; }
	public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
