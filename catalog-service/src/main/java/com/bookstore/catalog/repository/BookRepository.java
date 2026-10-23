package com.bookstore.catalog.repository;

import com.bookstore.catalog.entity.Book;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import org.springframework.data.domain.Pageable;

public interface BookRepository extends JpaRepository<Book, Long> {
    @Query("""
            select b from Book b
            where b.id <> :bookId
              and ((:subcategory is not null and lower(b.subcategory) = lower(:subcategory))
                   or (:category is not null and lower(b.category) = lower(:category)))
            order by b.isBestSeller desc, b.title asc
            """)
    List<Book> findRelated(@Param("bookId") Long bookId, @Param("category") String category,
                           @Param("subcategory") String subcategory, Pageable pageable);
    boolean existsByTitleIgnoreCase(String title);
    @Modifying
    @Transactional
    @Query(value = "update books set image_url = :imageUrl where lower(title) = lower(:title)", nativeQuery = true)
    int updateImageUrlByTitleIgnoreCase(@Param("title") String title, @Param("imageUrl") String imageUrl);

    @Modifying
    @Transactional
    @Query("update Book b set b.category = :category, b.subcategory = :subcategory, " +
            "b.publisher = :publisher, b.language = :language, b.format = :format, " +
            "b.conditionLabel = 'New', b.listingNote = :listingNote where lower(b.title) = lower(:title)")
    int updateCatalogMetadataByTitleIgnoreCase(@Param("title") String title,
            @Param("category") String category, @Param("subcategory") String subcategory,
            @Param("publisher") String publisher, @Param("language") String language,
            @Param("format") String format, @Param("listingNote") String listingNote);

    @Modifying
    @Transactional
    @Query("update Book b set b.conditionLabel = :conditionLabel, b.price = :price " +
            "where lower(b.title) = lower(:title)")
    int markDemoUsedByTitleIgnoreCase(@Param("title") String title, @Param("conditionLabel") String conditionLabel,
                                      @Param("price") java.math.BigDecimal price);
    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author);
    List<Book> findByIsNewArrivalTrue();
    List<Book> findByIsBestSellerTrue();

    @Query("""
            select b from Book b
            where lower(b.title) like lower(concat('%', :query, '%'))
               or lower(b.author) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.description, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.category, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.subcategory, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.publisher, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.specifications, '')) like lower(concat('%', :query, '%'))
            order by b.title asc
            """)
    List<Book> searchProducts(@Param("query") String query);

    @Query("""
            select b from Book b
            where lower(b.title) like lower(concat('%', :query, '%'))
               or lower(b.author) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.description, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.category, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.subcategory, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.publisher, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(b.specifications, '')) like lower(concat('%', :query, '%'))
            order by
              case
                when lower(b.title) like lower(concat(:query, '%')) then 0
                when lower(b.title) like lower(concat('%', :query, '%')) then 1
                when lower(b.author) like lower(concat(:query, '%')) then 2
                when lower(b.author) like lower(concat('%', :query, '%')) then 3
                else 4
              end,
              b.title asc
            """)
    List<Book> suggestProducts(@Param("query") String query, Pageable pageable);

    @Modifying
    @Query("update Book b set b.stock = b.stock - :quantity where b.id = :bookId and b.stock >= :quantity")
    int decreaseStockIfAvailable(@Param("bookId") Long bookId, @Param("quantity") Integer quantity);

    @Modifying
    @Query("update Book b set b.stock = b.stock + :quantity where b.id = :bookId")
    int increaseStock(@Param("bookId") Long bookId, @Param("quantity") Integer quantity);
}
