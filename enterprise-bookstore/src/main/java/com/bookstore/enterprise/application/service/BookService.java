package com.bookstore.enterprise.application.service;

import com.bookstore.enterprise.application.dto.book.BookRequest;
import com.bookstore.enterprise.application.dto.book.BookResponse;

import java.util.List;

public interface BookService {

    List<BookResponse> getAllBooks();

    BookResponse getBookById(Long id);

    BookResponse createBook(BookRequest request);

    BookResponse updateBook(Long id, BookRequest request);

    void deleteBook(Long id);
}
