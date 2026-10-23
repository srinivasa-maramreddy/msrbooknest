package com.bookstore.catalog.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class ProductRequest {
    @NotBlank(message = "Title is required")
    @Size(max = 255, message = "Title must be 255 characters or less")
    private String title;

    @NotBlank(message = "Author is required")
    @Size(max = 255, message = "Author must be 255 characters or less")
    private String author;

    @Size(max = 2000, message = "Description must be 2000 characters or less")
    private String description;

    @Size(max = 4000, message = "Synopsis must be 4000 characters or less")
    private String synopsis;

    @Size(max = 500, message = "Intended audience must be 500 characters or less")
    private String intendedAudience;

    private LocalDate publicationDate;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Price must have at most two decimal places")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Original price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Original price must have at most two decimal places")
    private BigDecimal originalPrice;

    @NotNull(message = "Stock is required")
    @PositiveOrZero(message = "Stock cannot be negative")
    private Integer stock;

    @Size(max = 1200, message = "Image URL must be 1200 characters or less")
    private String imageUrl;

    @Size(max = 120)
    private String category;

    @Size(max = 160)
    private String subcategory;

    @Size(max = 40)
    private String conditionLabel;

    @Size(max = 160)
    private String publisher;

    @Size(max = 120)
    private String edition;

    @Size(max = 80)
    private String language;

    @Size(max = 40)
    private String isbn;

    @Size(max = 80)
    private String format;

    @Size(max = 4000)
    private String specifications;

    @Size(max = 120)
    private String listingNote;

    @JsonProperty("isNewArrival")
    private Boolean isNewArrival = false;

    @JsonProperty("isBestSeller")
    private Boolean isBestSeller = false;

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public String getSynopsis() { return synopsis; }
    public String getIntendedAudience() { return intendedAudience; }
    public LocalDate getPublicationDate() { return publicationDate; }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getOriginalPrice() { return originalPrice; }

    public Integer getStock() {
        return stock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getCategory() { return category; }
    public String getSubcategory() { return subcategory; }
    public String getConditionLabel() { return conditionLabel; }
    public String getPublisher() { return publisher; }
    public String getEdition() { return edition; }
    public String getLanguage() { return language; }
    public String getIsbn() { return isbn; }
    public String getFormat() { return format; }
    public String getSpecifications() { return specifications; }
    public String getListingNote() { return listingNote; }

    @JsonProperty("isNewArrival")
    public Boolean getIsNewArrival() {
        return isNewArrival;
    }

    @JsonProperty("isBestSeller")
    public Boolean getIsBestSeller() {
        return isBestSeller;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }
    public void setIntendedAudience(String intendedAudience) { this.intendedAudience = intendedAudience; }
    public void setPublicationDate(LocalDate publicationDate) { this.publicationDate = publicationDate; }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public void setCategory(String category) { this.category = category; }
    public void setSubcategory(String subcategory) { this.subcategory = subcategory; }
    public void setConditionLabel(String conditionLabel) { this.conditionLabel = conditionLabel; }
    public void setPublisher(String publisher) { this.publisher = publisher; }
    public void setEdition(String edition) { this.edition = edition; }
    public void setLanguage(String language) { this.language = language; }
    public void setIsbn(String isbn) { this.isbn = isbn; }
    public void setFormat(String format) { this.format = format; }
    public void setSpecifications(String specifications) { this.specifications = specifications; }
    public void setListingNote(String listingNote) { this.listingNote = listingNote; }

    @JsonProperty("isNewArrival")
    public void setIsNewArrival(Boolean isNewArrival) {
        this.isNewArrival = isNewArrival;
    }

    @JsonProperty("isBestSeller")
    public void setIsBestSeller(Boolean isBestSeller) {
        this.isBestSeller = isBestSeller;
    }
}
