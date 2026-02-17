package com.bookstore.enterprise.application.service.impl;

import com.bookstore.enterprise.application.dto.book.BookRequest;
import com.bookstore.enterprise.application.dto.book.BookResponse;
import com.bookstore.enterprise.application.exception.ResourceNotFoundException;
import com.bookstore.enterprise.application.mapper.BookMapper;
import com.bookstore.enterprise.application.service.BookService;
import com.bookstore.enterprise.domain.model.Author;
import com.bookstore.enterprise.domain.model.Book;
import com.bookstore.enterprise.domain.model.Category;
import com.bookstore.enterprise.domain.repository.AuthorRepository;
import com.bookstore.enterprise.domain.repository.BookRepository;
import com.bookstore.enterprise.domain.repository.CategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BookServiceImpl implements BookService {

    private static final Logger log = LoggerFactory.getLogger(BookServiceImpl.class);

    private final BookRepository bookRepository;
    private final AuthorRepository authorRepository;
    private final CategoryRepository categoryRepository;
    private final BookMapper bookMapper;

    public BookServiceImpl(
            BookRepository bookRepository,
            AuthorRepository authorRepository,
            CategoryRepository categoryRepository,
            BookMapper bookMapper
    ) {
        this.bookRepository = bookRepository;
        this.authorRepository = authorRepository;
        this.categoryRepository = categoryRepository;
        this.bookMapper = bookMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookResponse> getAllBooks() {
        return bookRepository.findAll().stream()
                .map(bookMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));
        return bookMapper.toResponse(book);
    }

    @Override
    @Transactional
    public BookResponse createBook(BookRequest request) {
        Author author = authorRepository.findById(request.getAuthorId())
                .orElseThrow(() -> new ResourceNotFoundException("Author not found with id " + request.getAuthorId()));
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id " + request.getCategoryId()));

        Book book = new Book(
                null,
                request.getIsbn(),
                request.getTitle(),
                request.getDescription(),
                request.getPrice(),
                request.getStock(),
                request.getCoverImageUrl(),
                request.isFeatured(),
                request.isBestSeller(),
                author,
                category
        );

        Book savedBook = bookRepository.save(book);
        log.info("Created book id={} title={} isbn={}", savedBook.getId(), savedBook.getTitle(), savedBook.getIsbn());
        return bookMapper.toResponse(savedBook);
    }

    @Override
    @Transactional
    public BookResponse updateBook(Long id, BookRequest request) {
        Book existingBook = bookRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + id));
        Author author = authorRepository.findById(request.getAuthorId())
                .orElseThrow(() -> new ResourceNotFoundException("Author not found with id " + request.getAuthorId()));
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id " + request.getCategoryId()));

        existingBook.setIsbn(request.getIsbn());
        existingBook.setTitle(request.getTitle());
        existingBook.setDescription(request.getDescription());
        existingBook.setPrice(request.getPrice());
        existingBook.setStock(request.getStock());
        existingBook.setCoverImageUrl(request.getCoverImageUrl());
        existingBook.setFeatured(request.isFeatured());
        existingBook.setBestSeller(request.isBestSeller());
        existingBook.setAuthor(author);
        existingBook.setCategory(category);

        Book savedBook = bookRepository.save(existingBook);
        log.info("Updated book id={} title={}", savedBook.getId(), savedBook.getTitle());
        return bookMapper.toResponse(savedBook);
    }

    @Override
    @Transactional
    public void deleteBook(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new ResourceNotFoundException("Book not found with id " + id);
        }
        bookRepository.deleteById(id);
        log.info("Deleted book id={}", id);
    }
}
