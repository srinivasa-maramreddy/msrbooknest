package com.bookstore.enterprise.domain.model;

import java.math.BigDecimal;

public class Book {

    private Long id;
    private String isbn;
    private String title;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private String coverImageUrl;
    private boolean featured;
    private boolean bestSeller;
    private Author author;
    private Category category;

    public Book() {
    }

    public Book(
            Long id,
            String isbn,
            String title,
            String description,
            BigDecimal price,
            Integer stock,
            String coverImageUrl,
            boolean featured,
            boolean bestSeller,
            Author author,
            Category category
    ) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.coverImageUrl = coverImageUrl;
        this.featured = featured;
        this.bestSeller = bestSeller;
        this.author = author;
        this.category = category;
    }

    public boolean hasInventory(int quantity) {
        return stock != null && stock >= quantity;
    }

    public void reduceStock(int quantity) {
        if (stock == null) {
            stock = 0;
        }
        stock -= quantity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStock() {
        return stock;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public boolean isFeatured() {
        return featured;
    }

    public void setFeatured(boolean featured) {
        this.featured = featured;
    }

    public boolean isBestSeller() {
        return bestSeller;
    }

    public void setBestSeller(boolean bestSeller) {
        this.bestSeller = bestSeller;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }
}
