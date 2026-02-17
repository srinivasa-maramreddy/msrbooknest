package com.bookstore.enterprise.infrastructure.persistence.adapter;

import com.bookstore.enterprise.domain.model.Category;
import com.bookstore.enterprise.domain.repository.CategoryRepository;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaCategoryEntity;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaCategoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final JpaCategoryRepository jpaCategoryRepository;

    public CategoryRepositoryAdapter(JpaCategoryRepository jpaCategoryRepository) {
        this.jpaCategoryRepository = jpaCategoryRepository;
    }

    @Override
    public Optional<Category> findById(Long id) {
        return jpaCategoryRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return jpaCategoryRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private Category toDomain(JpaCategoryEntity entity) {
        return new Category(entity.getId(), entity.getName(), entity.getDescription());
    }
}
