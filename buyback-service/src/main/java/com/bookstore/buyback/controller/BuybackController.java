package com.bookstore.buyback.controller;

import com.bookstore.buyback.domain.BuybackCase;
import com.bookstore.buyback.dto.BuybackRequests;
import com.bookstore.buyback.service.BuybackWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/buybacks")
public class BuybackController {
    private final BuybackWorkflowService workflow;

    public BuybackController(BuybackWorkflowService workflow) { this.workflow = workflow; }

    @GetMapping
    public List<BuybackCase> list(Authentication authentication) {
        return workflow.list(email(authentication), isAdmin(authentication));
    }

    @GetMapping("/{id}")
    public BuybackCase get(@PathVariable String id, Authentication authentication) {
        return workflow.get(id, email(authentication), isAdmin(authentication));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BuybackCase create(@Valid @RequestBody BuybackRequests.CreateCaseRequest request, Authentication authentication) {
        return workflow.create(email(authentication), request);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("hasRole('ADMIN')")
    public BuybackCase review(@PathVariable String id, @RequestBody(required = false) BuybackRequests.NoteRequest request, Authentication authentication) {
        return workflow.startReview(id, email(authentication), request);
    }

    @PostMapping("/{id}/quote")
    @PreAuthorize("hasRole('ADMIN')")
    public BuybackCase quote(@PathVariable String id, @Valid @RequestBody BuybackRequests.QuoteRequest request) {
        return workflow.offerQuote(id, request, false);
    }

    @PostMapping("/{id}/quote/decision")
    public BuybackCase quoteDecision(@PathVariable String id, @Valid @RequestBody BuybackRequests.QuoteDecisionRequest request, Authentication authentication) {
        return workflow.decideQuote(id, email(authentication), request);
    }

    @PostMapping("/{id}/shipment")
    public BuybackCase shipment(@PathVariable String id, @Valid @RequestBody BuybackRequests.TrackingRequest request, Authentication authentication) {
        return workflow.submitShipment(id, email(authentication), request);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasRole('ADMIN')")
    public BuybackCase receive(@PathVariable String id) { return workflow.receive(id); }

    @PostMapping("/{id}/inspection")
    @PreAuthorize("hasRole('ADMIN')")
    public BuybackCase inspection(@PathVariable String id, @Valid @RequestBody BuybackRequests.InspectionRequest request) {
        return workflow.inspect(id, request);
    }

    @PostMapping("/{id}/failure-choice")
    public BuybackCase failureChoice(@PathVariable String id, @Valid @RequestBody BuybackRequests.FailureChoiceRequest request, Authentication authentication) {
        return workflow.chooseAfterFailure(id, email(authentication), request);
    }

    @PostMapping("/{id}/revision")
    @PreAuthorize("hasRole('ADMIN')")
    public BuybackCase revision(@PathVariable String id, @Valid @RequestBody BuybackRequests.QuoteRequest request) {
        return workflow.offerQuote(id, request, true);
    }

    @PostMapping("/{id}/revision/decision")
    public BuybackCase revisionDecision(@PathVariable String id, @Valid @RequestBody BuybackRequests.RevisionDecisionRequest request, Authentication authentication) {
        return workflow.decideRevision(id, email(authentication), request);
    }

    @PostMapping("/{id}/return/dispatch")
    @PreAuthorize("hasRole('ADMIN')")
    public BuybackCase dispatchReturn(@PathVariable String id, @Valid @RequestBody BuybackRequests.TrackingRequest request) {
        return workflow.dispatchReturn(id, request);
    }

    @PostMapping("/{id}/return/deliver")
    @PreAuthorize("hasRole('ADMIN')")
    public BuybackCase deliverReturn(@PathVariable String id) { return workflow.deliverReturn(id); }

    @PostMapping("/{id}/dispute/resolve")
    @PreAuthorize("hasRole('ADMIN')")
    public BuybackCase resolveDispute(@PathVariable String id, @Valid @RequestBody BuybackRequests.DisputeResolutionRequest request) {
        return workflow.resolveDispute(id, request);
    }

    private String email(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in is required");
        String email = jwt.getClaimAsString("email");
        return email == null || email.isBlank() ? jwt.getSubject() : email;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream().anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
