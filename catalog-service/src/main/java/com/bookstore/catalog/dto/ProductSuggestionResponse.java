package com.bookstore.catalog.dto;

import java.math.BigDecimal;

import com.bookstore.catalog.entity.Book;

public class ProductSuggestionResponse {
    private Long id;
    private String title;
    private String author;
    private BigDecimal price;
    private String imageUrl;

    public ProductSuggestionResponse() {
    }

    public ProductSuggestionResponse(Book book) {
        this.id = book.getId();
        this.title = book.getTitle();
        this.author = book.getAuthor();
        this.price = book.getPrice();
        this.imageUrl = book.getImageUrl();
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

    public BigDecimal getPrice() {
        return price;
    }

    public String getImageUrl() {
        return imageUrl;
    }
}
