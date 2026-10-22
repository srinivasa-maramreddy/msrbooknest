package com.bookstore.cart.service;

import java.math.BigDecimal;

import com.bookstore.cart.dto.AddCartItemRequest;
import com.bookstore.cart.dto.CartItemResponse;
import com.bookstore.cart.dto.UpdateCartItemRequest;
import com.bookstore.cart.entity.CartItem;
import com.bookstore.cart.repository.CartItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Transactional
public class CartService {
    private final CartItemRepository cartItemRepository;
    private final RestClient catalogClient;

    public CartService(CartItemRepository cartItemRepository, RestClient.Builder restClientBuilder,
                       @Value("${app.services.catalog-base-url:http://localhost:8082}") String catalogBaseUrl) {
        this.cartItemRepository = cartItemRepository;
        this.catalogClient = restClientBuilder.baseUrl(catalogBaseUrl).build();
    }

    public List<CartItemResponse> getCart(String customerEmail) {
        return cartItemRepository.findByCustomerEmailOrderByUpdatedAtDesc(customerEmail).stream()
                .map(this::toResponse)
                .toList();
    }

    public List<CartItemResponse> addItem(String customerEmail, AddCartItemRequest request) {
        if (request.getQuantity() == null || request.getQuantity() <= 0) {
            throw new RuntimeException("Quantity must be greater than zero");
        }

        CatalogBookResponse book = fetchBook(request.getBookId());
        CartItem cartItem = cartItemRepository.findByCustomerEmailAndBookId(customerEmail, request.getBookId())
                .orElseGet(CartItem::new);

        int newQuantity = request.getQuantity() + (cartItem.getQuantity() == null ? 0 : cartItem.getQuantity());
        if (book.getStock() < newQuantity) {
            throw new RuntimeException("Requested quantity exceeds available stock");
        }

        cartItem.setCustomerEmail(customerEmail);
        cartItem.setBookId(book.getId());
        cartItem.setTitle(book.getTitle());
        cartItem.setAuthor(book.getAuthor());
        cartItem.setImageUrl(book.getImageUrl());
        cartItem.setUnitPrice(book.getPrice());
        cartItem.setQuantity(newQuantity);

        cartItemRepository.save(cartItem);
        return getCart(customerEmail);
    }

    public List<CartItemResponse> updateQuantity(String customerEmail, Long bookId, UpdateCartItemRequest request) {
        if (request.getQuantity() == null) {
            throw new RuntimeException("Quantity is required");
        }

        if (request.getQuantity() <= 0) {
            cartItemRepository.deleteByCustomerEmailAndBookId(customerEmail, bookId);
            return getCart(customerEmail);
        }

        CatalogBookResponse book = fetchBook(bookId);
        if (book.getStock() < request.getQuantity()) {
            throw new RuntimeException("Requested quantity exceeds available stock");
        }

        CartItem cartItem = cartItemRepository.findByCustomerEmailAndBookId(customerEmail, bookId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        cartItem.setTitle(book.getTitle());
        cartItem.setAuthor(book.getAuthor());
        cartItem.setImageUrl(book.getImageUrl());
        cartItem.setUnitPrice(book.getPrice());
        cartItem.setQuantity(request.getQuantity());
        cartItemRepository.save(cartItem);
        return getCart(customerEmail);
    }

    public List<CartItemResponse> removeItem(String customerEmail, Long bookId) {
        cartItemRepository.deleteByCustomerEmailAndBookId(customerEmail, bookId);
        return getCart(customerEmail);
    }

    public void clearCart(String customerEmail) {
        cartItemRepository.deleteByCustomerEmail(customerEmail);
    }

    private CartItemResponse toResponse(CartItem item) {
        return new CartItemResponse(
                item.getBookId(),
                item.getTitle(),
                item.getAuthor(),
                item.getImageUrl(),
                item.getUnitPrice(),
                item.getQuantity()
        );
    }

    private CatalogBookResponse fetchBook(Long bookId) {
        try {
            CatalogBookResponse response = catalogClient.get()
                    .uri("/api/books/{id}", bookId)
                    .retrieve()
                    .body(CatalogBookResponse.class);
            if (response == null) {
                throw new RuntimeException("Book not found");
            }
            if (response.getStock() == null || response.getStock() <= 0) {
                throw new RuntimeException("Book is out of stock");
            }
            return response;
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException("Could not fetch book details from catalog service");
        }
    }

    public static class CatalogBookResponse {
        private Long id;
        private String title;
        private String author;
        private String imageUrl;
        private BigDecimal price;
        private Integer stock;

        public Long getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthor() {
            return author;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public Integer getStock() {
            return stock;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public void setAuthor(String author) {
            this.author = author;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }
    }
}
