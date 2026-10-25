package com.bookstore.catalog.service;

import com.bookstore.catalog.entity.Book;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Selects a compact, representative set for customer-facing catalog responses. */
public final class StorefrontCatalogSampler {
    public static final int LIMIT = 100;

    private StorefrontCatalogSampler() {
    }

    public static List<Book> select(List<Book> books) {
        if (books.size() <= LIMIT) {
            return books;
        }

        Map<String, Book> selected = new LinkedHashMap<>();
        for (Book book : books) {
            if (isPriorityTitle(book)) {
                selected.put(key(book), book);
            }
        }
        for (Book book : books) {
            if (Boolean.TRUE.equals(book.getIsBestSeller()) && selected.size() < 24) {
                selected.putIfAbsent(key(book), book);
            }
        }

        int remaining = LIMIT - selected.size();
        for (int sample = 0; sample < remaining; sample++) {
            int index = (int) (((long) (sample + 1) * books.size()) / (remaining + 1));
            Book book = books.get(Math.min(index, books.size() - 1));
            selected.putIfAbsent(key(book), book);
        }

        if (selected.size() < LIMIT) {
            for (Book book : books) {
                selected.putIfAbsent(key(book), book);
                if (selected.size() == LIMIT) {
                    break;
                }
            }
        }

        return new ArrayList<>(selected.values()).subList(0, LIMIT);
    }

    private static boolean isPriorityTitle(Book book) {
        String title = safeLower(book.getTitle());
        String publisher = safeLower(book.getPublisher());
        return title.startsWith("cengage ") || title.startsWith("arihant ")
                || publisher.contains("ncert") || publisher.contains("national council of educational research");
    }

    private static String key(Book book) {
        return book.getId() == null ? safeLower(book.getTitle()) : book.getId().toString();
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
