package com.bookstore.enterprise.infrastructure.persistence.adapter;

import com.bookstore.enterprise.domain.model.Author;
import com.bookstore.enterprise.domain.repository.AuthorRepository;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaAuthorEntity;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaAuthorRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class AuthorRepositoryAdapter implements AuthorRepository {

    private final JpaAuthorRepository jpaAuthorRepository;

    public AuthorRepositoryAdapter(JpaAuthorRepository jpaAuthorRepository) {
        this.jpaAuthorRepository = jpaAuthorRepository;
    }

    @Override
    public Optional<Author> findById(Long id) {
        return jpaAuthorRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<Author> findAll() {
        return jpaAuthorRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private Author toDomain(JpaAuthorEntity entity) {
        return new Author(entity.getId(), entity.getName(), entity.getBio());
    }
}
