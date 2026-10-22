package com.bookstore.catalog.service;

import com.bookstore.catalog.dto.InventoryBookQuote;
import com.bookstore.catalog.dto.InventoryDeductRequest;
import com.bookstore.catalog.dto.InventoryItemRequest;
import com.bookstore.catalog.dto.InventoryQuoteRequest;
import com.bookstore.catalog.dto.InventoryQuoteResponse;
import com.bookstore.catalog.dto.InventoryReservationRequest;
import com.bookstore.catalog.dto.ProductRequest;
import com.bookstore.catalog.dto.ProductResponse;
import com.bookstore.catalog.dto.ProductSuggestionResponse;
import com.bookstore.catalog.entity.Book;
import com.bookstore.catalog.entity.InventoryReservation;
import com.bookstore.catalog.entity.InventoryReservationItem;
import com.bookstore.catalog.entity.InventoryReservationStatus;
import com.bookstore.catalog.repository.BookRepository;
import com.bookstore.catalog.repository.InventoryReservationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;

@Service
public class BookService {
    private final BookRepository bookRepository;
    private final InventoryReservationRepository reservationRepository;

    public BookService(BookRepository bookRepository, InventoryReservationRepository reservationRepository) {
        this.bookRepository = bookRepository;
        this.reservationRepository = reservationRepository;
    }

    public List<Book> getAll() {
        return bookRepository.findAll();
    }

    public List<Book> search(String q) {
        return bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(q, q);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> searchProducts(String query) {
        List<Book> books = query == null || query.isBlank()
                ? bookRepository.findAll()
                : bookRepository.searchProducts(query.trim());

        return books.stream()
                .map(ProductResponse::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ProductSuggestionResponse> suggestProducts(String query, Integer limit) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }

        int size = limit == null ? 8 : Math.max(1, Math.min(limit, 12));
        return bookRepository.suggestProducts(query.trim(), PageRequest.of(0, size)).stream()
                .map(ProductSuggestionResponse::new)
                .toList();
    }

    public Book getById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found"));
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductById(Long id) {
        return new ProductResponse(getById(id));
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

    @Transactional
    public ProductResponse createProduct(ProductRequest request) {
        Book book = new Book();
        applyProductRequest(book, request);
        return new ProductResponse(bookRepository.save(book));
    }

    public Book update(Long id, Book payload) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        book.setTitle(payload.getTitle());
        book.setAuthor(payload.getAuthor());
        book.setDescription(payload.getDescription());
        book.setPrice(payload.getPrice());
        book.setStock(payload.getStock());
        book.setImageUrl(payload.getImageUrl());
        book.setCategory(payload.getCategory());
        book.setSubcategory(payload.getSubcategory());
        book.setConditionLabel(payload.getConditionLabel());
        book.setPublisher(payload.getPublisher());
        book.setEdition(payload.getEdition());
        book.setLanguage(payload.getLanguage());
        book.setIsbn(payload.getIsbn());
        book.setFormat(payload.getFormat());
        book.setSpecifications(payload.getSpecifications());
        book.setListingNote(payload.getListingNote());
        book.setIsNewArrival(payload.getIsNewArrival());
        book.setIsBestSeller(payload.getIsBestSeller());
        return bookRepository.save(book);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Book not found"));
        applyProductRequest(book, request);
        return new ProductResponse(bookRepository.save(book));
    }

    @Transactional(readOnly = true)
    public InventoryQuoteResponse quoteInventory(InventoryQuoteRequest request) {
        Map<Long, Integer> requestedQuantities = aggregateRequest(request == null ? null : request.getItems());
        if (requestedQuantities.isEmpty()) {
            throw new RuntimeException("At least one inventory item is required");
        }

        List<InventoryBookQuote> quotes = requestedQuantities.entrySet().stream()
                .map(entry -> buildQuote(entry.getKey(), entry.getValue()))
                .toList();

        BigDecimal totalAmount = quotes.stream()
                .map(InventoryBookQuote::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        InventoryQuoteResponse response = new InventoryQuoteResponse();
        response.setItems(quotes);
        response.setTotalAmount(totalAmount);
        return response;
    }

    @Transactional
    public InventoryQuoteResponse deductInventory(InventoryDeductRequest request) {
        InventoryQuoteResponse quoted = quoteInventory(toQuoteRequest(request));
        for (InventoryBookQuote quote : quoted.getItems()) {
            int updated = bookRepository.decreaseStockIfAvailable(quote.getBookId(), quote.getQuantity());
            if (updated == 0) {
                throw new RuntimeException("Insufficient stock for book id " + quote.getBookId());
            }
        }
        return quoted;
    }

    @Transactional
    public InventoryQuoteResponse reserveInventory(InventoryReservationRequest request) {
        if (request == null || request.getOrderRequestId() == null || request.getOrderRequestId().isBlank()) {
            throw new RuntimeException("orderRequestId is required");
        }
        InventoryReservation existing = reservationRepository.findByOrderRequestId(request.getOrderRequestId())
                .orElse(null);
        if (existing != null) {
            if (existing.getStatus() == InventoryReservationStatus.RELEASED) {
                throw new RuntimeException("Inventory reservation was already released");
            }
            return reservationQuote(existing);
        }

        Map<Long, Integer> requestedQuantities = aggregateRequest(request.getItems());
        if (requestedQuantities.isEmpty()) {
            throw new RuntimeException("At least one inventory item is required");
        }

        InventoryReservation reservation = new InventoryReservation();
        reservation.setOrderRequestId(request.getOrderRequestId());
        reservation.setExpiresAt(java.time.LocalDateTime.now().plusMinutes(20));
        List<InventoryBookQuote> quotes = new java.util.ArrayList<>();
        for (Map.Entry<Long, Integer> entry : requestedQuantities.entrySet()) {
            InventoryBookQuote quote = buildQuote(entry.getKey(), entry.getValue());
            if (bookRepository.decreaseStockIfAvailable(quote.getBookId(), quote.getQuantity()) == 0) {
                throw new RuntimeException("Insufficient stock for book id " + quote.getBookId());
            }
            InventoryReservationItem reserved = new InventoryReservationItem();
            reserved.setReservation(reservation);
            reserved.setBookId(quote.getBookId());
            reserved.setQuantity(quote.getQuantity());
            reserved.setTitle(quote.getTitle());
            reserved.setAuthor(quote.getAuthor());
            reserved.setImageUrl(quote.getImageUrl());
            reserved.setUnitPrice(quote.getUnitPrice());
            reservation.getItems().add(reserved);
            quotes.add(quote);
        }
        reservationRepository.save(reservation);
        return quoteResponse(quotes);
    }

    @Transactional
    public void confirmReservation(String orderRequestId) {
        reservationRepository.findByOrderRequestId(orderRequestId).ifPresent(reservation -> {
            if (reservation.getStatus() == InventoryReservationStatus.HELD) {
                reservation.setStatus(InventoryReservationStatus.COMMITTED);
                reservationRepository.save(reservation);
            }
        });
    }

    @Transactional
    public void releaseReservation(String orderRequestId) {
        reservationRepository.findByOrderRequestId(orderRequestId).ifPresent(reservation -> {
            if (reservation.getStatus() != InventoryReservationStatus.HELD) return;
            for (InventoryReservationItem item : reservation.getItems()) {
                bookRepository.increaseStock(item.getBookId(), item.getQuantity());
            }
            reservation.setStatus(InventoryReservationStatus.RELEASED);
            reservationRepository.save(reservation);
        });
    }

    @Scheduled(fixedDelayString = "${catalog.inventory.expiration-scan-ms:60000}")
    @Transactional
    public void releaseExpiredReservations() {
        reservationRepository.findTop100ByStatusAndExpiresAtBeforeOrderByExpiresAtAsc(
                        InventoryReservationStatus.HELD, java.time.LocalDateTime.now())
                .forEach(reservation -> {
                    for (InventoryReservationItem item : reservation.getItems()) {
                        bookRepository.increaseStock(item.getBookId(), item.getQuantity());
                    }
                    reservation.setStatus(InventoryReservationStatus.RELEASED);
                });
    }

    public void delete(Long id) {
        if (!bookRepository.existsById(id)) {
            throw new RuntimeException("Book not found");
        }
        bookRepository.deleteById(id);
    }

    private void applyProductRequest(Book book, ProductRequest request) {
        book.setTitle(request.getTitle().trim());
        book.setAuthor(request.getAuthor().trim());
        book.setDescription(request.getDescription() == null ? "" : request.getDescription().trim());
        book.setPrice(request.getPrice());
        book.setStock(request.getStock());
        book.setImageUrl(request.getImageUrl() == null ? null : request.getImageUrl().trim());
        book.setCategory(blankToNull(request.getCategory()));
        book.setSubcategory(blankToNull(request.getSubcategory()));
        book.setConditionLabel(request.getConditionLabel() == null || request.getConditionLabel().isBlank()
                ? "New" : request.getConditionLabel().trim());
        book.setPublisher(blankToNull(request.getPublisher()));
        book.setEdition(blankToNull(request.getEdition()));
        book.setLanguage(blankToNull(request.getLanguage()));
        book.setIsbn(blankToNull(request.getIsbn()));
        book.setFormat(blankToNull(request.getFormat()));
        book.setSpecifications(blankToNull(request.getSpecifications()));
        book.setListingNote(blankToNull(request.getListingNote()));
        book.setIsNewArrival(Boolean.TRUE.equals(request.getIsNewArrival()));
        book.setIsBestSeller(Boolean.TRUE.equals(request.getIsBestSeller()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private InventoryQuoteRequest toQuoteRequest(InventoryDeductRequest request) {
        InventoryQuoteRequest quoteRequest = new InventoryQuoteRequest();
        quoteRequest.setItems(request == null ? null : request.getItems());
        return quoteRequest;
    }

    private Map<Long, Integer> aggregateRequest(List<InventoryItemRequest> items) {
        Map<Long, Integer> aggregated = new LinkedHashMap<>();
        if (items == null) {
            return aggregated;
        }

        for (InventoryItemRequest item : items) {
            if (item == null || item.getBookId() == null || item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new RuntimeException("Each inventory item must include bookId and quantity > 0");
            }
            aggregated.merge(item.getBookId(), item.getQuantity(), Integer::sum);
        }
        return aggregated;
    }

    private InventoryBookQuote buildQuote(Long bookId, Integer quantity) {
        Book book = getById(bookId);
        if (book.getStock() == null || book.getStock() < quantity) {
            throw new RuntimeException("Insufficient stock for book id " + bookId);
        }

        InventoryBookQuote quote = new InventoryBookQuote();
        quote.setBookId(book.getId());
        quote.setTitle(book.getTitle());
        quote.setAuthor(book.getAuthor());
        quote.setImageUrl(book.getImageUrl());
        quote.setUnitPrice(book.getPrice());
        quote.setQuantity(quantity);
        quote.setLineTotal(book.getPrice().multiply(BigDecimal.valueOf(quantity)));
        return quote;
    }

    private InventoryQuoteResponse reservationQuote(InventoryReservation reservation) {
        List<InventoryBookQuote> quotes = reservation.getItems().stream()
                .map(item -> {
                    InventoryBookQuote quote = new InventoryBookQuote();
                    quote.setBookId(item.getBookId());
                    quote.setTitle(item.getTitle());
                    quote.setAuthor(item.getAuthor());
                    quote.setImageUrl(item.getImageUrl());
                    quote.setQuantity(item.getQuantity());
                    quote.setUnitPrice(item.getUnitPrice());
                    quote.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                    return quote;
                })
                .toList();
        return quoteResponse(quotes);
    }

    private InventoryQuoteResponse quoteResponse(List<InventoryBookQuote> quotes) {
        InventoryQuoteResponse response = new InventoryQuoteResponse();
        response.setItems(quotes);
        response.setTotalAmount(quotes.stream()
                .map(InventoryBookQuote::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return response;
    }
}
