package com.bookstore.catalog.entity;

import java.math.BigDecimal;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_reservation_items")
public class InventoryReservationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private InventoryReservation reservation;

    @Column(nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    private String imageUrl;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    public Long getId() { return id; }
    public InventoryReservation getReservation() { return reservation; }
    public Long getBookId() { return bookId; }
    public Integer getQuantity() { return quantity; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getImageUrl() { return imageUrl; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setReservation(InventoryReservation reservation) { this.reservation = reservation; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public void setTitle(String title) { this.title = title; }
    public void setAuthor(String author) { this.author = author; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
