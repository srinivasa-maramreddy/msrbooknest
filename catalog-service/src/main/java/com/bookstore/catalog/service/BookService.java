package com.bookstore.catalog.service;

import com.bookstore.catalog.entity.Book;
import com.bookstore.catalog.repository.BookRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BookService {
    private final BookRepository bookRepository;

    public BookService(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<Book> getAll() {
        return bookRepository.findAll();
    }

    public List<Book> search(String q) {
        return bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(q, q);
    }

    public List<Book> getNewArrivals() {
        return bookRepository.findByIsNewArrivalTrue();
    }

    public List<Book> getBestSellers() {
        return bookRepository.findByIsBestSellerTrue();
    }

    public Book save(Book book) {
        return bookRepository.save(book);
    }

    public Book update(Long id, Book payload) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        book.setTitle(payload.getTitle());
        book.setAuthor(payload.getAuthor());
        book.setDescription(payload.getDescription());
        book.setPrice(payload.getPrice());
        book.setStock(payload.getStock());
        book.setIsNewArrival(payload.getIsNewArrival());
        book.setIsBestSeller(payload.getIsBestSeller());
        return bookRepository.save(book);
    }

    public void delete(Long id) {
        bookRepository.deleteById(id);
    }
}
