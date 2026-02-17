package com.bookstore.enterprise.application.service;

import com.bookstore.enterprise.application.dto.book.BookRequest;
import com.bookstore.enterprise.application.dto.book.BookResponse;
import com.bookstore.enterprise.application.exception.ResourceNotFoundException;
import com.bookstore.enterprise.application.mapper.BookMapper;
import com.bookstore.enterprise.application.service.impl.BookServiceImpl;
import com.bookstore.enterprise.domain.model.Author;
import com.bookstore.enterprise.domain.model.Book;
import com.bookstore.enterprise.domain.model.Category;
import com.bookstore.enterprise.domain.repository.AuthorRepository;
import com.bookstore.enterprise.domain.repository.BookRepository;
import com.bookstore.enterprise.domain.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookServiceImplTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private AuthorRepository authorRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private BookServiceImpl bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookServiceImpl(bookRepository, authorRepository, categoryRepository, new BookMapper());
    }

    @Test
    void createBookShouldPersistAndReturnBookResponse() {
        BookRequest request = new BookRequest();
        request.setIsbn("9789999999999");
        request.setTitle("Clean Architecture in Practice");
        request.setDescription("Book description");
        request.setPrice(new BigDecimal("399.00"));
        request.setStock(25);
        request.setCoverImageUrl("https://example.com/book.jpg");
        request.setFeatured(true);
        request.setBestSeller(false);
        request.setAuthorId(1L);
        request.setCategoryId(2L);

        Author author = new Author(1L, "Author Name", "Author bio");
        Category category = new Category(2L, "Tech", "Tech books");

        when(authorRepository.findById(1L)).thenReturn(Optional.of(author));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(bookRepository.save(any(Book.class))).thenAnswer(invocation -> {
            Book saved = invocation.getArgument(0, Book.class);
            saved.setId(101L);
            return saved;
        });

        BookResponse response = bookService.createBook(request);

        assertEquals(101L, response.getId());
        assertEquals("Clean Architecture in Practice", response.getTitle());
        assertEquals("Author Name", response.getAuthorName());
        assertEquals("Tech", response.getCategoryName());

        ArgumentCaptor<Book> captor = ArgumentCaptor.forClass(Book.class);
        verify(bookRepository).save(captor.capture());
        assertEquals("9789999999999", captor.getValue().getIsbn());
    }

    @Test
    void createBookShouldThrowWhenAuthorMissing() {
        BookRequest request = new BookRequest();
        request.setAuthorId(99L);
        request.setCategoryId(2L);

        when(authorRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bookService.createBook(request));
    }
}
