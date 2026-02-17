package com.bookstore.enterprise.domain.repository;

import com.bookstore.enterprise.domain.model.BookOrder;

import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    BookOrder save(BookOrder order);

    Optional<BookOrder> findById(Long id);

    List<BookOrder> findByUserId(Long userId);

    List<BookOrder> findAll();
}
