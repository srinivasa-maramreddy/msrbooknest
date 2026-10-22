package com.bookstore.catalog.dto;

import java.math.BigDecimal;

import com.bookstore.catalog.entity.Book;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ProductResponse {
    private Long id;
    private String title;
    private String author;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String imageUrl;
    private String category;
    private String subcategory;
    private String conditionLabel;
    private String publisher;
    private String edition;
    private String language;
    private String isbn;
    private String format;
    private String specifications;
    private String listingNote;

    @JsonProperty("isNewArrival")
    private Boolean isNewArrival;

    @JsonProperty("isBestSeller")
    private Boolean isBestSeller;

    public ProductResponse() {
    }

    public ProductResponse(Book book) {
        this.id = book.getId();
        this.title = book.getTitle();
        this.author = book.getAuthor();
        this.description = book.getDescription();
        this.price = book.getPrice();
        this.stock = book.getStock();
        this.imageUrl = book.getImageUrl();
        this.category = book.getCategory();
        this.subcategory = book.getSubcategory();
        this.conditionLabel = book.getConditionLabel();
        this.publisher = book.getPublisher();
        this.edition = book.getEdition();
        this.language = book.getLanguage();
        this.isbn = book.getIsbn();
        this.format = book.getFormat();
        this.specifications = book.getSpecifications();
        this.listingNote = book.getListingNote();
        this.isNewArrival = book.getIsNewArrival();
        this.isBestSeller = book.getIsBestSeller();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

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
}
