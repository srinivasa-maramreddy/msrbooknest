package com.bookstore.enterprise.application.mapper;

import com.bookstore.enterprise.application.dto.book.BookResponse;
import com.bookstore.enterprise.domain.model.Book;
import org.springframework.stereotype.Component;

@Component
public class BookMapper {

    public BookResponse toResponse(Book book) {
        BookResponse response = new BookResponse();
        response.setId(book.getId());
        response.setIsbn(book.getIsbn());
        response.setTitle(book.getTitle());
        response.setDescription(book.getDescription());
        response.setPrice(book.getPrice());
        response.setStock(book.getStock());
        response.setCoverImageUrl(book.getCoverImageUrl());
        response.setFeatured(book.isFeatured());
        response.setBestSeller(book.isBestSeller());

        if (book.getAuthor() != null) {
            response.setAuthorId(book.getAuthor().getId());
            response.setAuthorName(book.getAuthor().getName());
        }
        if (book.getCategory() != null) {
            response.setCategoryId(book.getCategory().getId());
            response.setCategoryName(book.getCategory().getName());
        }

        return response;
    }
}
