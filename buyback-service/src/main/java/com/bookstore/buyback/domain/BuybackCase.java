package com.bookstore.buyback.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "buyback_cases")
public class BuybackCase {
    @Id
    private String id = UUID.randomUUID().toString();
    @Column(nullable = false, length = 254)
    private String customerEmail;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(nullable = false, length = 160)
    private String author;
    @Column(length = 32)
    private String isbn;
    @Column(nullable = false, length = 32)
    private String conditionGrade;
    @Column(length = 2000)
    private String customerNotes;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BuybackStatus status = BuybackStatus.SUBMITTED;
    private Integer quoteVersion = 0;
    @Column(length = 120)
    private String inboundTrackingNumber;
    @Column(length = 120)
    private String returnTrackingNumber;
    @Column(length = 1500)
    private String inspectionEvidence;
    @Column(length = 1000)
    private String disputeReason;
    @Column(length = 60)
    private String disputeDecision;
    @Column(length = 1000)
    private String disputeRationale;
    @Column(precision = 10, scale = 2)
    private BigDecimal returnFee;
    @Column(length = 24)
    private String returnFeePayer;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Version
    private Long entityVersion;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "buyback_quotes", joinColumns = @JoinColumn(name = "case_id"))
    @OrderColumn(name = "quote_order")
    private List<QuoteOffer> quotes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "buyback_photos", joinColumns = @JoinColumn(name = "case_id"))
    @OrderColumn(name = "photo_order")
    private List<BookPhoto> photos = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "buyback_activity", joinColumns = @JoinColumn(name = "case_id"))
    @OrderColumn(name = "activity_order")
    private List<ActivityEntry> timeline = new ArrayList<>();

    public void addActivity(String actor, String message) {
        timeline.add(new ActivityEntry(actor, message, LocalDateTime.now()));
        updatedAt = LocalDateTime.now();
    }

    public void addPhoto(String category, String fileName, String contentType, String dataUrl) {
        photos.add(new BookPhoto(category, fileName, contentType, dataUrl));
    }

    public String getId() { return id; }
    public String getCustomerEmail() { return customerEmail; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getIsbn() { return isbn; }
    public String getConditionGrade() { return conditionGrade; }
    public String getCustomerNotes() { return customerNotes; }
    public BuybackStatus getStatus() { return status; }
    public Integer getQuoteVersion() { return quoteVersion; }
    public String getInboundTrackingNumber() { return inboundTrackingNumber; }
    public String getReturnTrackingNumber() { return returnTrackingNumber; }
    public String getInspectionEvidence() { return inspectionEvidence; }
    public String getDisputeReason() { return disputeReason; }
    public String getDisputeDecision() { return disputeDecision; }
    public String getDisputeRationale() { return disputeRationale; }
    public BigDecimal getReturnFee() { return returnFee; }
    public String getReturnFeePayer() { return returnFeePayer; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<QuoteOffer> getQuotes() { return quotes; }
    public List<BookPhoto> getPhotos() { return photos; }
    public List<ActivityEntry> getTimeline() { return timeline; }
    public void setCustomerEmail(String value) { customerEmail = value; }
    public void setTitle(String value) { title = value; }
    public void setAuthor(String value) { author = value; }
    public void setIsbn(String value) { isbn = value; }
    public void setConditionGrade(String value) { conditionGrade = value; }
    public void setCustomerNotes(String value) { customerNotes = value; }
    public void setStatus(BuybackStatus value) { status = value; updatedAt = LocalDateTime.now(); }
    public void setQuoteVersion(Integer value) { quoteVersion = value; }
    public void setInboundTrackingNumber(String value) { inboundTrackingNumber = value; }
    public void setReturnTrackingNumber(String value) { returnTrackingNumber = value; }
    public void setInspectionEvidence(String value) { inspectionEvidence = value; }
    public void setDisputeReason(String value) { disputeReason = value; }
    public void setDisputeDecision(String value) { disputeDecision = value; }
    public void setDisputeRationale(String value) { disputeRationale = value; }
    public void setReturnFee(BigDecimal value) { returnFee = value; }
    public void setReturnFeePayer(String value) { returnFeePayer = value; }

    @Embeddable
    public static class QuoteOffer {
        @Column(name = "version_number", nullable = false)
        private Integer versionNumber;
        @Column(name = "amount", nullable = false, precision = 10, scale = 2)
        private BigDecimal amount;
        @Column(name = "reason", length = 800)
        private String reason;
        @Column(name = "quote_state", length = 20, nullable = false)
        private String quoteState;
        @Column(name = "offered_at", nullable = false)
        private LocalDateTime offeredAt;

        protected QuoteOffer() { }
        public QuoteOffer(Integer version, BigDecimal amount, String reason) {
            this.versionNumber = version;
            this.amount = amount;
            this.reason = reason;
            this.quoteState = "PENDING";
            this.offeredAt = LocalDateTime.now();
        }
        public Integer getVersionNumber() { return versionNumber; }
        public BigDecimal getAmount() { return amount; }
        public String getReason() { return reason; }
        public String getQuoteState() { return quoteState; }
        public LocalDateTime getOfferedAt() { return offeredAt; }
        public void setQuoteState(String value) { quoteState = value; }
    }

    @Embeddable
    public static class BookPhoto {
        @Column(name = "category", length = 24, nullable = false)
        private String category;
        @Column(name = "file_name", length = 180)
        private String fileName;
        @Column(name = "content_type", length = 80)
        private String contentType;
        @Lob
        @Column(name = "data_url", nullable = false)
        private String dataUrl;

        protected BookPhoto() { }
        public BookPhoto(String category, String fileName, String contentType, String dataUrl) {
            this.category = category;
            this.fileName = fileName;
            this.contentType = contentType;
            this.dataUrl = dataUrl;
        }
        public String getCategory() { return category; }
        public String getFileName() { return fileName; }
        public String getContentType() { return contentType; }
        public String getDataUrl() { return dataUrl; }
    }

    @Embeddable
    public static class ActivityEntry {
        @Column(name = "actor", length = 24, nullable = false)
        private String actor;
        @Column(name = "message", length = 800, nullable = false)
        private String message;
        @Column(name = "created_at", nullable = false)
        private LocalDateTime createdAt;

        protected ActivityEntry() { }
        public ActivityEntry(String actor, String message, LocalDateTime createdAt) {
            this.actor = actor;
            this.message = message;
            this.createdAt = createdAt;
        }
        public String getActor() { return actor; }
        public String getMessage() { return message; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}
