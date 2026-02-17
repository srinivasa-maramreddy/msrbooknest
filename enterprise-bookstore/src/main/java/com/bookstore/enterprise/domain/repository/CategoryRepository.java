package com.bookstore.enterprise.domain.repository;

import com.bookstore.enterprise.domain.model.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    Optional<Category> findById(Long id);

    List<Category> findAll();
}
