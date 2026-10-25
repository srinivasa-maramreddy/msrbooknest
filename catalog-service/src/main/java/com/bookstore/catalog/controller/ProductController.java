package com.bookstore.catalog.controller;

import com.bookstore.catalog.dto.ProductRequest;
import com.bookstore.catalog.service.BookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final BookService bookService;

    public ProductController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping("/products")
    public ResponseEntity<?> getProducts(@RequestParam(value = "q", required = false) String query) {
        return ResponseEntity.ok(bookService.getStorefrontProducts(query));
    }

    @GetMapping("/products/search")
    public ResponseEntity<?> searchProducts(@RequestParam(value = "q", required = false) String query) {
        return ResponseEntity.ok(bookService.getStorefrontProducts(query));
    }

    @GetMapping("/products/suggestions")
    public ResponseEntity<?> suggestProducts(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", required = false) Integer limit
    ) {
        return ResponseEntity.ok(bookService.suggestProducts(query, limit));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<?> getProductDetails(@PathVariable("id") Long id) {
        try {
            return ResponseEntity.ok(bookService.getProductById(id));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/products/{id}/related")
    public ResponseEntity<?> getRelatedProducts(@PathVariable("id") Long id,
                                                @RequestParam(value = "limit", required = false, defaultValue = "8") int limit) {
        try {
            return ResponseEntity.ok(bookService.getRelatedProducts(id, limit));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/admin/products")
    public ResponseEntity<?> adminGetProducts(
            @RequestParam(value = "q", required = false) String query,
            HttpServletRequest request
    ) {
        ResponseEntity<?> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(bookService.searchProducts(query));
    }

    @GetMapping("/admin/products/search")
    public ResponseEntity<?> adminSearchProducts(
            @RequestParam(value = "q", required = false) String query,
            HttpServletRequest request
    ) {
        return adminGetProducts(query, request);
    }

    @GetMapping("/admin/products/suggestions")
    public ResponseEntity<?> adminSuggestProducts(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "limit", required = false) Integer limit,
            HttpServletRequest request
    ) {
        ResponseEntity<?> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(bookService.suggestProducts(query, limit));
    }

    @GetMapping("/admin/products/{id}")
    public ResponseEntity<?> adminGetProductDetails(@PathVariable("id") Long id, HttpServletRequest request) {
        ResponseEntity<?> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return getProductDetails(id);
    }

    @PostMapping("/admin/products")
    public ResponseEntity<?> addProduct(@Valid @RequestBody ProductRequest productRequest, HttpServletRequest request) {
        ResponseEntity<?> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(bookService.createProduct(productRequest));
    }

    @PutMapping("/admin/products/{id}")
    public ResponseEntity<?> updateProduct(
            @PathVariable("id") Long id,
            @Valid @RequestBody ProductRequest productRequest,
            HttpServletRequest request
    ) {
        ResponseEntity<?> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        try {
            return ResponseEntity.ok(bookService.updateProduct(id, productRequest));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/admin/products/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable("id") Long id, HttpServletRequest request) {
        ResponseEntity<?> guard = requireAdmin(request);
        if (guard != null) {
            return guard;
        }
        try {
            bookService.delete(id);
            return ResponseEntity.ok(Map.of("message", "Product deleted"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    private ResponseEntity<?> requireAdmin(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "JWT token required"));
        }
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Admin role required"));
        }
        return null;
    }
}
