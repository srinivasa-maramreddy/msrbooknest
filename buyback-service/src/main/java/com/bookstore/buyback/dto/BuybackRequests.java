package com.bookstore.buyback.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

public final class BuybackRequests {
    private BuybackRequests() { }

    public record PhotoRequest(@NotBlank @Size(max = 180) String fileName, @NotBlank @Size(max = 80) String contentType,
                               @NotBlank @Size(max = 700_000) String dataUrl) { }

    public record CreateCaseRequest(@NotBlank @Size(max = 200) String title,
                                    @NotBlank @Size(max = 160) String author,
                                    @Size(max = 32) String isbn,
                                    @NotBlank @Pattern(regexp = "(?i)LIKE_NEW|VERY_GOOD|GOOD|ACCEPTABLE") String conditionGrade,
                                    @Size(max = 2000) String customerNotes,
                                    @NotEmpty @Size(max = 4) List<@Valid PhotoRequest> photos) { }

    public record NoteRequest(@Size(max = 800) String note) { }
    public record QuoteRequest(@NotNull @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal amount,
                               @NotBlank @Size(max = 800) String reason) { }
    public record QuoteDecisionRequest(@NotNull Integer quoteVersion, @NotNull Boolean accept) { }
    public record TrackingRequest(@NotBlank @Size(max = 120) String trackingNumber) { }
    public record InspectionRequest(@NotNull Boolean passed, @NotBlank @Size(max = 1500) String evidence,
                                    @Size(max = 3) List<@Valid PhotoRequest> photos) { }
    public record FailureChoiceRequest(@NotBlank @Pattern(regexp = "RETURN|REVISION|DISPUTE") String choice,
                                      @Size(max = 1000) String reason) { }
    public record RevisionDecisionRequest(@NotNull Integer quoteVersion, @NotNull Boolean accept) { }
    public record DisputeResolutionRequest(@NotBlank @Pattern(regexp = "PAY_ORIGINAL|OFFER_REVISED|RETURN_STORE_PAYS|RETURN_CUSTOMER_PAYS") String decision,
                                           @Size(max = 800) String rationale,
                                           @DecimalMin("1.00") @Digits(integer = 8, fraction = 2) BigDecimal revisedAmount) { }
}
