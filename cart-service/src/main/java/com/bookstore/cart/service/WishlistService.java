package com.bookstore.cart.service;

import com.bookstore.cart.entity.WishlistItem;
import com.bookstore.cart.repository.WishlistItemRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Transactional
public class WishlistService {
    private final WishlistItemRepository repository;
    private final RestClient catalogClient;

    public WishlistService(WishlistItemRepository repository, RestClient.Builder restClientBuilder,
                           @Value("${app.services.catalog-base-url:http://localhost:8082}") String catalogBaseUrl) {
        this.repository = repository;
        this.catalogClient = restClientBuilder.baseUrl(catalogBaseUrl).build();
    }

    @Transactional(readOnly = true)
    public List<Long> getWishlist(String email) {
        return repository.findByCustomerEmailOrderByCreatedAtDesc(email).stream().map(WishlistItem::getBookId).toList();
    }

    public List<Long> add(String email, Long bookId) {
        if (bookId == null || bookId < 1) throw new IllegalArgumentException("A valid book id is required");
        if (repository.findByCustomerEmailAndBookId(email, bookId).isPresent()) return getWishlist(email);
        try {
            CatalogBookResponse book = catalogClient.get().uri("/api/books/{id}", bookId).retrieve().body(CatalogBookResponse.class);
            if (book == null || book.id == null) throw new IllegalArgumentException("Book not found");
        } catch (RuntimeException ex) {
            if (ex instanceof IllegalArgumentException) throw ex;
            throw new IllegalArgumentException("Could not verify this book with the catalog service");
        }
        WishlistItem item = new WishlistItem();
        item.setCustomerEmail(email);
        item.setBookId(bookId);
        try {
            repository.save(item);
        } catch (DataIntegrityViolationException ignored) {
            // Concurrent requests for the same customer's book converge on the unique pair.
        }
        return getWishlist(email);
    }

    public List<Long> remove(String email, Long bookId) {
        repository.deleteByCustomerEmailAndBookId(email, bookId);
        return getWishlist(email);
    }

    private static final class CatalogBookResponse {
        private Long id;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
