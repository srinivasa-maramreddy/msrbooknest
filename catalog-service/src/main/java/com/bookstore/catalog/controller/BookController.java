package com.bookstore.catalog.controller;

import com.bookstore.catalog.entity.Book;
import com.bookstore.catalog.service.BookService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/books")
public class BookController {
    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    public ResponseEntity<?> all(@RequestParam(value = "q", required = false) String q) {
        if (q != null && !q.isBlank()) {
            return ResponseEntity.ok(bookService.search(q));
        }
        return ResponseEntity.ok(bookService.getAll());
    }

    @GetMapping("/new-arrivals")
    public ResponseEntity<?> newArrivals() {
        return ResponseEntity.ok(bookService.getNewArrivals());
    }

    @GetMapping("/best-sellers")
    public ResponseEntity<?> bestSellers() {
        return ResponseEntity.ok(bookService.getBestSellers());
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Book book, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin role required"));
        }
        return ResponseEntity.ok(bookService.save(book));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable("id") Long id, @RequestBody Book book, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin role required"));
        }
        try {
            return ResponseEntity.ok(bookService.update(id, book));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        if (!isAdmin(request)) {
            return ResponseEntity.status(403).body(Map.of("error", "Admin role required"));
        }
        bookService.delete(id);
        return ResponseEntity.ok(Map.of("message", "Deleted"));
    }

    private boolean isAdmin(HttpServletRequest request) {
        Object role = request.getAttribute("userRole");
        return role != null && "ADMIN".equalsIgnoreCase(role.toString());
    }
}
