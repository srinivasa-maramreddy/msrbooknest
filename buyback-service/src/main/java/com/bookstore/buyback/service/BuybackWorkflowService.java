package com.bookstore.buyback.service;

import com.bookstore.buyback.domain.BuybackCase;
import com.bookstore.buyback.domain.BuybackPayment;
import com.bookstore.buyback.domain.BuybackStatus;
import com.bookstore.buyback.dto.BuybackRequests;
import com.bookstore.buyback.messaging.BuybackEventPublisher;
import com.bookstore.buyback.repository.BuybackCaseRepository;
import com.bookstore.buyback.repository.BuybackPaymentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class BuybackWorkflowService {
    private static final BigDecimal RETURN_FEE = new BigDecimal("120.00");
    private static final String STORE_ADDRESS = "MSR Booknest Buyback Desk, 18 Residency Road, Bengaluru, Karnataka 560025";
    private final BuybackCaseRepository cases;
    private final BuybackPaymentRepository payments;
    private final BuybackEventPublisher events;

    public BuybackWorkflowService(BuybackCaseRepository cases, BuybackPaymentRepository payments, BuybackEventPublisher events) {
        this.cases = cases;
        this.payments = payments;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<BuybackCase> list(String email, boolean admin) {
        return admin ? cases.findAllByOrderByCreatedAtDesc() : cases.findAllByCustomerEmailOrderByCreatedAtDesc(email);
    }

    @Transactional(readOnly = true)
    public BuybackCase get(String id, String email, boolean admin) {
        BuybackCase buyback = cases.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyback request not found"));
        authorizeOwner(buyback, email, admin);
        return buyback;
    }

    @Transactional
    public BuybackCase create(String email, BuybackRequests.CreateCaseRequest request) {
        BuybackCase buyback = new BuybackCase();
        buyback.setCustomerEmail(email);
        buyback.setTitle(request.title().trim());
        buyback.setAuthor(request.author().trim());
        buyback.setIsbn(clean(request.isbn()));
        buyback.setConditionGrade(request.conditionGrade().toUpperCase());
        buyback.setCustomerNotes(clean(request.customerNotes()));
        for (BuybackRequests.PhotoRequest photo : safeList(request.photos())) addPhoto(buyback, photo, "BOOK");
        buyback.addActivity("CUSTOMER", "Submitted this book for buyback review.");
        return save(buyback, "CASE_SUBMITTED");
    }

    @Transactional
    public BuybackCase startReview(String id, String adminEmail, BuybackRequests.NoteRequest request) {
        BuybackCase buyback = locked(id);
        requireStatus(buyback, BuybackStatus.SUBMITTED);
        buyback.setStatus(BuybackStatus.UNDER_REVIEW);
        String note = request == null ? null : clean(request.note());
        buyback.addActivity("ADMIN", "Mock video verification completed" + (note == null ? "." : ": " + note));
        return save(buyback, "VIDEO_REVIEW_COMPLETED");
    }

    @Transactional
    public BuybackCase offerQuote(String id, BuybackRequests.QuoteRequest request, boolean revised) {
        BuybackCase buyback = locked(id);
        requireStatus(buyback, revised ? BuybackStatus.REVISION_REQUESTED : BuybackStatus.UNDER_REVIEW);
        int version = buyback.getQuoteVersion() + 1;
        supersedePendingQuote(buyback);
        buyback.getQuotes().add(new BuybackCase.QuoteOffer(version, request.amount(), request.reason().trim()));
        buyback.setQuoteVersion(version);
        buyback.setStatus(revised ? BuybackStatus.REVISED_QUOTE_PENDING : BuybackStatus.QUOTED);
        buyback.addActivity("ADMIN", (revised ? "Revised quotation v" : "Quotation v") + version + " offered: ₹" + request.amount().toPlainString() + ". Reason: " + request.reason().trim());
        return save(buyback, revised ? "REVISED_QUOTE_OFFERED" : "QUOTE_OFFERED");
    }

    @Transactional
    public BuybackCase decideQuote(String id, String email, BuybackRequests.QuoteDecisionRequest request) {
        BuybackCase buyback = ownedLocked(id, email);
        if (buyback.getStatus() != BuybackStatus.QUOTED && buyback.getStatus() != BuybackStatus.REVISED_QUOTE_PENDING)
            throw new IllegalStateException("There is no quotation waiting for your response.");
        BuybackCase.QuoteOffer quote = currentQuote(buyback, request.quoteVersion());
        if (!"PENDING".equals(quote.getQuoteState())) throw new IllegalStateException("This quotation was already answered.");
        boolean revision = buyback.getStatus() == BuybackStatus.REVISED_QUOTE_PENDING;
        quote.setQuoteState(Boolean.TRUE.equals(request.accept()) ? "ACCEPTED" : "REJECTED");
        if (Boolean.TRUE.equals(request.accept())) {
            buyback.addActivity("CUSTOMER", "Accepted quotation v" + quote.getVersionNumber() + " for ₹" + quote.getAmount().toPlainString() + ". The agreed amount is locked.");
            if (revision) {
                return completePayment(buyback, quote.getAmount(), "Revised quotation accepted");
            }
            buyback.setStatus(BuybackStatus.AWAITING_SHIPMENT);
            buyback.addActivity("SYSTEM", "Ship to " + STORE_ADDRESS + " using DTDC. Inbound courier charges are paid by the customer.");
            return save(buyback, "QUOTE_ACCEPTED");
        }
        buyback.addActivity("CUSTOMER", "Rejected quotation v" + quote.getVersionNumber() + ".");
        buyback.setStatus(revision ? BuybackStatus.INSPECTION_FAILED : BuybackStatus.QUOTE_DECLINED);
        return save(buyback, "QUOTE_REJECTED");
    }

    @Transactional
    public BuybackCase submitShipment(String id, String email, BuybackRequests.TrackingRequest request) {
        BuybackCase buyback = ownedLocked(id, email);
        requireStatus(buyback, BuybackStatus.AWAITING_SHIPMENT);
        buyback.setInboundTrackingNumber(request.trackingNumber().trim());
        buyback.setStatus(BuybackStatus.IN_TRANSIT);
        buyback.addActivity("CUSTOMER", "Book shipped through DTDC. Mock tracking number: " + request.trackingNumber().trim());
        return save(buyback, "CUSTOMER_SHIPMENT_RECORDED");
    }

    @Transactional
    public BuybackCase receive(String id) {
        BuybackCase buyback = locked(id);
        requireStatus(buyback, BuybackStatus.IN_TRANSIT);
        buyback.setStatus(BuybackStatus.RECEIVED);
        buyback.addActivity("ADMIN", "Book marked received. Physical inspection is ready to begin.");
        return save(buyback, "BOOK_RECEIVED");
    }

    @Transactional
    public BuybackCase inspect(String id, BuybackRequests.InspectionRequest request) {
        BuybackCase buyback = locked(id);
        if (buyback.getStatus() == BuybackStatus.PAYMENT_COMPLETED && Boolean.TRUE.equals(request.passed())) return buyback;
        requireStatus(buyback, BuybackStatus.RECEIVED);
        buyback.setInspectionEvidence(request.evidence().trim());
        for (BuybackRequests.PhotoRequest photo : safeList(request.photos())) addPhoto(buyback, photo, "INSPECTION");
        if (Boolean.TRUE.equals(request.passed())) {
            buyback.addActivity("ADMIN", "Physical inspection passed: " + request.evidence().trim());
            return completePayment(buyback, acceptedQuote(buyback).getAmount(), "Physical inspection passed");
        }
        buyback.setStatus(BuybackStatus.INSPECTION_FAILED);
        buyback.addActivity("ADMIN", "Physical inspection failed. Evidence: " + request.evidence().trim());
        return save(buyback, "INSPECTION_FAILED");
    }

    @Transactional
    public BuybackCase chooseAfterFailure(String id, String email, BuybackRequests.FailureChoiceRequest request) {
        BuybackCase buyback = ownedLocked(id, email);
        requireStatus(buyback, BuybackStatus.INSPECTION_FAILED);
        String choice = request.choice().toUpperCase();
        switch (choice) {
            case "RETURN" -> {
                requestReturn(buyback, "STORE", "Customer requested return after failed inspection. The store pays the mock ₹120 return label.");
                return save(buyback, "RETURN_REQUESTED");
            }
            case "REVISION" -> {
                buyback.setStatus(BuybackStatus.REVISION_REQUESTED);
                buyback.addActivity("CUSTOMER", "Requested a revised quotation" + (clean(request.reason()) == null ? "." : ": " + request.reason().trim()));
                return save(buyback, "REVISION_REQUESTED");
            }
            case "DISPUTE" -> {
                if (clean(request.reason()) == null) throw new IllegalArgumentException("Add a short reason for the dispute.");
                buyback.setDisputeReason(request.reason().trim());
                buyback.setStatus(BuybackStatus.DISPUTE_OPEN);
                buyback.addActivity("CUSTOMER", "Opened a dispute: " + request.reason().trim());
                return save(buyback, "DISPUTE_OPENED");
            }
            default -> throw new IllegalArgumentException("Choose return, revised quotation, or dispute.");
        }
    }

    @Transactional
    public BuybackCase decideRevision(String id, String email, BuybackRequests.RevisionDecisionRequest request) {
        BuybackCase buyback = ownedLocked(id, email);
        requireStatus(buyback, BuybackStatus.REVISED_QUOTE_PENDING);
        BuybackCase.QuoteOffer quote = currentQuote(buyback, request.quoteVersion());
        if (!"PENDING".equals(quote.getQuoteState())) throw new IllegalStateException("This revised quotation was already answered.");
        if (Boolean.TRUE.equals(request.accept())) {
            quote.setQuoteState("ACCEPTED");
            buyback.addActivity("CUSTOMER", "Accepted revised quotation v" + quote.getVersionNumber() + " for ₹" + quote.getAmount().toPlainString() + ".");
            return completePayment(buyback, quote.getAmount(), "Revised quotation accepted");
        }
        quote.setQuoteState("REJECTED");
        buyback.setStatus(BuybackStatus.INSPECTION_FAILED);
        buyback.addActivity("CUSTOMER", "Rejected revised quotation v" + quote.getVersionNumber() + ". Choose return or open a dispute.");
        return save(buyback, "REVISED_QUOTE_REJECTED");
    }

    @Transactional
    public BuybackCase dispatchReturn(String id, BuybackRequests.TrackingRequest request) {
        BuybackCase buyback = locked(id);
        requireStatus(buyback, BuybackStatus.RETURN_REQUESTED);
        buyback.setReturnTrackingNumber(request.trackingNumber().trim());
        buyback.setStatus(BuybackStatus.RETURN_DISPATCHED);
        buyback.addActivity("ADMIN", "Return dispatched through mock DTDC. Tracking number: " + request.trackingNumber().trim() + ". Fee ₹" + buyback.getReturnFee().toPlainString() + " paid by " + payerLabel(buyback.getReturnFeePayer()) + ".");
        return save(buyback, "RETURN_DISPATCHED");
    }

    @Transactional
    public BuybackCase deliverReturn(String id) {
        BuybackCase buyback = locked(id);
        requireStatus(buyback, BuybackStatus.RETURN_DISPATCHED);
        buyback.setStatus(BuybackStatus.RETURN_DELIVERED);
        buyback.addActivity("ADMIN", "Return marked delivered. Mock shipment is complete.");
        return save(buyback, "RETURN_DELIVERED");
    }

    @Transactional
    public BuybackCase resolveDispute(String id, BuybackRequests.DisputeResolutionRequest request) {
        BuybackCase buyback = locked(id);
        requireStatus(buyback, BuybackStatus.DISPUTE_OPEN);
        String decision = request.decision().toUpperCase();
        if (decision.equals("OFFER_REVISED") && request.revisedAmount() == null)
            throw new IllegalArgumentException("Enter the revised amount before recording this decision.");
        buyback.setDisputeDecision(decision);
        buyback.setDisputeRationale(clean(request.rationale()));
        buyback.addActivity("ADMIN", "Dispute decision: " + decision.replace('_', ' ') + ". " + (clean(request.rationale()) == null ? "" : request.rationale().trim()));
        switch (decision) {
            case "PAY_ORIGINAL" -> {
                return completePayment(buyback, originalAcceptedQuote(buyback).getAmount(), "Dispute resolved in customer’s favor");
            }
            case "OFFER_REVISED" -> {
                supersedePendingQuote(buyback);
                buyback.getQuotes().add(new BuybackCase.QuoteOffer(buyback.getQuoteVersion() + 1, request.revisedAmount(), clean(request.rationale()) == null ? "Dispute review decision" : request.rationale().trim()));
                buyback.setQuoteVersion(buyback.getQuoteVersion() + 1);
                buyback.setStatus(BuybackStatus.REVISED_QUOTE_PENDING);
                return save(buyback, "DISPUTE_RESOLVED_REVISED_OFFER");
            }
            case "RETURN_STORE_PAYS" -> {
                requestReturn(buyback, "STORE", "Dispute decision: store pays the ₹120 mock return fee.");
                return save(buyback, "DISPUTE_RESOLVED_RETURN_STORE_PAYS");
            }
            case "RETURN_CUSTOMER_PAYS" -> {
                requestReturn(buyback, "CUSTOMER", "Dispute decision: customer pays the ₹120 mock return fee.");
                return save(buyback, "DISPUTE_RESOLVED_RETURN_CUSTOMER_PAYS");
            }
            default -> throw new IllegalArgumentException("Select a valid final dispute decision.");
        }
    }

    private BuybackCase completePayment(BuybackCase buyback, BigDecimal amount, String reason) {
        var existing = payments.findByBuybackId(buyback.getId());
        if (existing.isPresent()) {
            if (buyback.getStatus() != BuybackStatus.PAYMENT_COMPLETED) {
                buyback.setStatus(BuybackStatus.PAYMENT_COMPLETED);
                buyback.addActivity("SYSTEM", "Existing mock payout " + existing.get().getReference() + " retained; no duplicate payment was created.");
                return save(buyback, "PAYMENT_ALREADY_COMPLETED");
            }
            return buyback;
        }
        BuybackPayment payment = payments.save(new BuybackPayment(buyback.getId(), amount));
        buyback.setStatus(BuybackStatus.PAYMENT_COMPLETED);
        buyback.addActivity("SYSTEM", reason + ". Mock payment of ₹" + amount.toPlainString() + " completed once. Reference: " + payment.getReference() + ".");
        return save(buyback, "PAYMENT_COMPLETED");
    }

    private BuybackCase save(BuybackCase buyback, String action) {
        BuybackCase saved = cases.save(buyback);
        events.statusChanged(saved, action);
        return saved;
    }

    private BuybackCase locked(String id) {
        return cases.findByIdForUpdate(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Buyback request not found"));
    }

    private BuybackCase ownedLocked(String id, String email) {
        BuybackCase buyback = locked(id);
        authorizeOwner(buyback, email, false);
        return buyback;
    }

    private void authorizeOwner(BuybackCase buyback, String email, boolean admin) {
        if (!admin && !buyback.getCustomerEmail().equalsIgnoreCase(email))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view your own buyback requests");
    }

    private void requireStatus(BuybackCase buyback, BuybackStatus expected) {
        if (buyback.getStatus() != expected)
            throw new IllegalStateException("This action is not available while the request is " + buyback.getStatus().name().replace('_', ' ').toLowerCase() + ".");
    }

    private BuybackCase.QuoteOffer currentQuote(BuybackCase buyback, Integer version) {
        if (version == null || !version.equals(buyback.getQuoteVersion()))
            throw new IllegalStateException("Quotation changed. Refresh and review the latest version before responding.");
        return buyback.getQuotes().stream().filter(quote -> quote.getVersionNumber().equals(version)).findFirst()
                .orElseThrow(() -> new IllegalStateException("Quotation not found."));
    }

    private BuybackCase.QuoteOffer acceptedQuote(BuybackCase buyback) {
        return buyback.getQuotes().stream().filter(quote -> "ACCEPTED".equals(quote.getQuoteState())).reduce((first, last) -> last)
                .orElseThrow(() -> new IllegalStateException("No accepted quotation exists for this request."));
    }

    private BuybackCase.QuoteOffer originalAcceptedQuote(BuybackCase buyback) {
        return buyback.getQuotes().stream().filter(quote -> quote.getVersionNumber() == 1).findFirst()
                .orElseThrow(() -> new IllegalStateException("Original quotation not found."));
    }

    private void supersedePendingQuote(BuybackCase buyback) {
        buyback.getQuotes().stream().filter(quote -> "PENDING".equals(quote.getQuoteState())).forEach(quote -> quote.setQuoteState("SUPERSEDED"));
    }

    private void requestReturn(BuybackCase buyback, String payer, String activity) {
        buyback.setReturnFee(RETURN_FEE);
        buyback.setReturnFeePayer(payer);
        buyback.setStatus(BuybackStatus.RETURN_REQUESTED);
        buyback.addActivity("SYSTEM", activity);
    }

    private String payerLabel(String payer) { return "CUSTOMER".equals(payer) ? "the customer" : "the store"; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private <T> List<T> safeList(List<T> values) { return values == null ? List.of() : values; }

    private void addPhoto(BuybackCase buyback, BuybackRequests.PhotoRequest photo, String category) {
        if (photo == null || photo.contentType() == null || !photo.contentType().toLowerCase().matches("image/(jpeg|png|webp)"))
            throw new IllegalArgumentException("Only JPG, PNG, or WebP photos are accepted.");
        if (photo.dataUrl() == null || !photo.dataUrl().startsWith("data:" + photo.contentType().toLowerCase() + ";base64,"))
            throw new IllegalArgumentException("The uploaded photo could not be read. Please choose it again.");
        buyback.addPhoto(category, photo.fileName(), photo.contentType().toLowerCase(), photo.dataUrl());
    }
}
