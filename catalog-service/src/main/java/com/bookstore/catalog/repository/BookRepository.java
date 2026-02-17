package com.bookstore.catalog.repository;

import com.bookstore.catalog.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author);
    List<Book> findByIsNewArrivalTrue();
    List<Book> findByIsBestSellerTrue();
}
