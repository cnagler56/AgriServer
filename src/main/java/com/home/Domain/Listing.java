package com.home.Domain;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * A Buy / Sell marketplace listing. Distinct from Post (forum-style) so the
 * /buysell feed can be filtered and categorized independently.
 */
@Entity
@Table(name = "listings")
public class Listing {

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	@JsonProperty
	private Long id;

	@JsonProperty
	private String title;

	@JsonProperty
	@Lob
	@Column(columnDefinition = "TEXT")
	private String description;

	/** "BUY" or "SELL" */
	@JsonProperty
	private String type;

	/** "TRACTORS", "COMBINES", "EQUIPMENT", "HAY", "LIVESTOCK", "SERVICES", "OTHER" */
	@JsonProperty
	private String category;

	/** Free-text price the lister wants to display (e.g. "$25,000", "$8/bale", "$45/hr"). */
	@JsonProperty
	private String price;

	/** Free-text quantity (e.g. "500 tons", "120 head", "20 acres"). Used mainly for hay / livestock / services. */
	@JsonProperty
	private String quantity;

	@JsonProperty
	private String name;

	@JsonProperty
	private String city;

	@JsonProperty
	private String state;

	/** Data-URL base64 of the uploaded photo (e.g. "data:image/jpeg;base64,..."). Nullable. */
	@JsonProperty
	@Lob
	@Column(columnDefinition = "TEXT")
	private String imageBase64;

	/** "PHONE", "TEXT", or "EMAIL" — how the lister wants to be contacted. */
	@JsonProperty
	private String contactMethod;

	/** The actual phone number or email address. */
	@JsonProperty
	private String contactValue;

	@JsonProperty
	@Column(name = "date")
	private LocalDateTime date;

	@JsonProperty
	private Long userId;

	@PrePersist
	void onCreate() {
		if (this.date == null) {
			this.date = LocalDateTime.now();
		}
	}

	public Long getId() { return id; }
	public void setId(Long id) { this.id = id; }

	public String getTitle() { return title; }
	public void setTitle(String title) { this.title = title; }

	public String getDescription() { return description; }
	public void setDescription(String description) { this.description = description; }

	public String getType() { return type; }
	public void setType(String type) { this.type = type; }

	public String getCategory() { return category; }
	public void setCategory(String category) { this.category = category; }

	public String getPrice() { return price; }
	public void setPrice(String price) { this.price = price; }

	public String getQuantity() { return quantity; }
	public void setQuantity(String quantity) { this.quantity = quantity; }

	public String getName() { return name; }
	public void setName(String name) { this.name = name; }

	public String getCity() { return city; }
	public void setCity(String city) { this.city = city; }

	public String getState() { return state; }
	public void setState(String state) { this.state = state; }

	public String getImageBase64() { return imageBase64; }
	public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

	public String getContactMethod() { return contactMethod; }
	public void setContactMethod(String contactMethod) { this.contactMethod = contactMethod; }

	public String getContactValue() { return contactValue; }
	public void setContactValue(String contactValue) { this.contactValue = contactValue; }

	public LocalDateTime getDate() { return date; }
	public void setDate(LocalDateTime date) { this.date = date; }

	public Long getUserId() { return userId; }
	public void setUserId(Long userId) { this.userId = userId; }
}
