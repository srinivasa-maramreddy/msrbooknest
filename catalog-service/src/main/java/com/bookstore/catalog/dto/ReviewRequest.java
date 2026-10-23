package com.bookstore.catalog.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ReviewRequest {
    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating;

    @NotBlank
    @Size(min = 3, max = 120)
    private String title;

    @NotBlank
    @Size(min = 20, max = 4000)
    private String comment;

    public Integer getRating() { return rating; }
    public String getTitle() { return title; }
    public String getComment() { return comment; }
    public void setRating(Integer rating) { this.rating = rating; }
    public void setTitle(String title) { this.title = title; }
    public void setComment(String comment) { this.comment = comment; }
}
