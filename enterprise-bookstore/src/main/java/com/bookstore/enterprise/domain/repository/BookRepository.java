package com.bookstore.enterprise.domain.repository;

import com.bookstore.enterprise.domain.model.Book;

import java.util.List;
import java.util.Optional;

public interface BookRepository {

    List<Book> findAll();

    Optional<Book> findById(Long id);

    Book save(Book book);

    void deleteById(Long id);

    boolean existsById(Long id);
}
