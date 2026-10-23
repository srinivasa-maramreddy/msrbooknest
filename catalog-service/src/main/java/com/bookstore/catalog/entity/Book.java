package com.bookstore.catalog.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.*;

@Entity
@Table(name = "books")
public class Book {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column(length = 2000)
    private String description;

    @Column(length = 4000)
    private String synopsis;

    @Column(length = 500)
    private String intendedAudience;

    private LocalDate publicationDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private Integer stock;

    @Column(length = 1200)
    private String imageUrl;

    @Column(length = 120)
    private String category;

    @Column(length = 160)
    private String subcategory;

    @Column(length = 40)
    private String conditionLabel = "New";

    @Column(length = 160)
    private String publisher;

    @Column(length = 120)
    private String edition;

    @Column(length = 80)
    private String language;

    @Column(length = 40)
    private String isbn;

    @Column(length = 80)
    private String format;

    @Column(columnDefinition = "TEXT")
    private String specifications;

    @Column(length = 120)
    private String listingNote;

    @Version
    private Long version;

    private Boolean isNewArrival = false;
    private Boolean isBestSeller = false;

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getSynopsis() { return synopsis; }
    public String getIntendedAudience() { return intendedAudience; }
    public LocalDate getPublicationDate() { return publicationDate; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public Integer getStock() { return stock; }
    public String getImageUrl() { return imageUrl; }
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
    public Long getVersion() { return version; }
    public Boolean getIsNewArrival() { return isNewArrival; }
    public Boolean getIsBestSeller() { return isBestSeller; }

    public void setId(Long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setDescription(String description) { this.description = description; }
    public void setSynopsis(String synopsis) { this.synopsis = synopsis; }
    public void setIntendedAudience(String intendedAudience) { this.intendedAudience = intendedAudience; }
    public void setPublicationDate(LocalDate publicationDate) { this.publicationDate = publicationDate; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public void setOriginalPrice(BigDecimal originalPrice) { this.originalPrice = originalPrice; }
    public void setStock(Integer stock) { this.stock = stock; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
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
    public void setVersion(Long version) { this.version = version; }
    public void setIsNewArrival(Boolean newArrival) { isNewArrival = newArrival; }
    public void setIsBestSeller(Boolean bestSeller) { isBestSeller = bestSeller; }
}
