package com.bookstore.enterprise.domain.repository;

import com.bookstore.enterprise.domain.model.Author;

import java.util.List;
import java.util.Optional;

public interface AuthorRepository {

    Optional<Author> findById(Long id);

    List<Author> findAll();
}
