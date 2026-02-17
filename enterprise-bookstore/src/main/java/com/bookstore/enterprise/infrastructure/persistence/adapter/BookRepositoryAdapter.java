package com.bookstore.enterprise.infrastructure.persistence.adapter;

import com.bookstore.enterprise.domain.model.Author;
import com.bookstore.enterprise.domain.model.Book;
import com.bookstore.enterprise.domain.model.Category;
import com.bookstore.enterprise.domain.repository.BookRepository;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaAuthorEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaBookEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaCategoryEntity;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaAuthorRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaBookRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaCategoryRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class BookRepositoryAdapter implements BookRepository {

    private final JpaBookRepository jpaBookRepository;
    private final JpaAuthorRepository jpaAuthorRepository;
    private final JpaCategoryRepository jpaCategoryRepository;

    public BookRepositoryAdapter(
            JpaBookRepository jpaBookRepository,
            JpaAuthorRepository jpaAuthorRepository,
            JpaCategoryRepository jpaCategoryRepository
    ) {
        this.jpaBookRepository = jpaBookRepository;
        this.jpaAuthorRepository = jpaAuthorRepository;
        this.jpaCategoryRepository = jpaCategoryRepository;
    }

    @Override
    public List<Book> findAll() {
        return jpaBookRepository.findAll().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Book> findById(Long id) {
        return jpaBookRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Book save(Book book) {
        JpaBookEntity entity = book.getId() == null
                ? new JpaBookEntity()
                : jpaBookRepository.findById(book.getId()).orElse(new JpaBookEntity());

        entity.setIsbn(book.getIsbn());
        entity.setTitle(book.getTitle());
        entity.setDescription(book.getDescription());
        entity.setPrice(book.getPrice());
        entity.setStock(book.getStock());
        entity.setCoverImageUrl(book.getCoverImageUrl());
        entity.setFeatured(book.isFeatured());
        entity.setBestSeller(book.isBestSeller());

        JpaAuthorEntity authorEntity = jpaAuthorRepository.findById(book.getAuthor().getId())
                .orElseThrow(() -> new IllegalArgumentException("Author not found"));
        JpaCategoryEntity categoryEntity = jpaCategoryRepository.findById(book.getCategory().getId())
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        entity.setAuthor(authorEntity);
        entity.setCategory(categoryEntity);

        JpaBookEntity saved = jpaBookRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void deleteById(Long id) {
        jpaBookRepository.deleteById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaBookRepository.existsById(id);
    }

    private Book toDomain(JpaBookEntity entity) {
        Author author = new Author(
                entity.getAuthor().getId(),
                entity.getAuthor().getName(),
                entity.getAuthor().getBio()
        );
        Category category = new Category(
                entity.getCategory().getId(),
                entity.getCategory().getName(),
                entity.getCategory().getDescription()
        );

        return new Book(
                entity.getId(),
                entity.getIsbn(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getPrice(),
                entity.getStock(),
                entity.getCoverImageUrl(),
                entity.isFeatured(),
                entity.isBestSeller(),
                author,
                category
        );
    }
}
