package com.bookstore.enterprise.infrastructure.config;

import com.bookstore.enterprise.infrastructure.persistence.entity.JpaAuthorEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaBookEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaCategoryEntity;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaAuthorRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaBookRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaCategoryRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.math.BigDecimal;
import java.util.List;

@Configuration
public class DataSeederConfig {

    @Bean
    @Profile("dev")
    CommandLineRunner seedCatalog(
            JpaAuthorRepository authorRepository,
            JpaCategoryRepository categoryRepository,
            JpaBookRepository bookRepository
    ) {
        return args -> {
            if (bookRepository.count() > 0) {
                return;
            }

            JpaAuthorEntity author1 = new JpaAuthorEntity();
            author1.setName("James Clear");
            author1.setBio("Author of Atomic Habits and productivity works.");

            JpaAuthorEntity author2 = new JpaAuthorEntity();
            author2.setName("Cal Newport");
            author2.setBio("Author focused on deep work and digital minimalism.");

            JpaAuthorEntity author3 = new JpaAuthorEntity();
            author3.setName("Morgan Housel");
            author3.setBio("Finance writer and author of The Psychology of Money.");

            List<JpaAuthorEntity> authors = authorRepository.saveAll(List.of(author1, author2, author3));

            JpaCategoryEntity category1 = new JpaCategoryEntity();
            category1.setName("Self-Help");
            category1.setDescription("Personal growth and productivity books.");

            JpaCategoryEntity category2 = new JpaCategoryEntity();
            category2.setName("Business");
            category2.setDescription("Business strategy and leadership.");

            JpaCategoryEntity category3 = new JpaCategoryEntity();
            category3.setName("Finance");
            category3.setDescription("Personal finance and investing.");

            List<JpaCategoryEntity> categories = categoryRepository.saveAll(List.of(category1, category2, category3));

            JpaBookEntity book1 = new JpaBookEntity();
            book1.setIsbn("9781847941831");
            book1.setTitle("Atomic Habits");
            book1.setDescription("Tiny changes, remarkable results.");
            book1.setPrice(new BigDecimal("499.00"));
            book1.setStock(60);
            book1.setCoverImageUrl("https://images.unsplash.com/photo-1544947950-fa07a98d237f");
            book1.setFeatured(true);
            book1.setBestSeller(true);
            book1.setAuthor(authors.get(0));
            book1.setCategory(categories.get(0));

            JpaBookEntity book2 = new JpaBookEntity();
            book2.setIsbn("9781455586691");
            book2.setTitle("Deep Work");
            book2.setDescription("Rules for focused success in a distracted world.");
            book2.setPrice(new BigDecimal("459.00"));
            book2.setStock(45);
            book2.setCoverImageUrl("https://images.unsplash.com/photo-1512820790803-83ca734da794");
            book2.setFeatured(true);
            book2.setBestSeller(false);
            book2.setAuthor(authors.get(1));
            book2.setCategory(categories.get(1));

            JpaBookEntity book3 = new JpaBookEntity();
            book3.setIsbn("9789390166268");
            book3.setTitle("The Psychology of Money");
            book3.setDescription("Timeless lessons on wealth and behavior.");
            book3.setPrice(new BigDecimal("399.00"));
            book3.setStock(70);
            book3.setCoverImageUrl("https://images.unsplash.com/photo-1516979187457-637abb4f9353");
            book3.setFeatured(false);
            book3.setBestSeller(true);
            book3.setAuthor(authors.get(2));
            book3.setCategory(categories.get(2));

            bookRepository.saveAll(List.of(book1, book2, book3));
        };
    }
}
