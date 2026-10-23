package com.bookstore.catalog.service;

import com.bookstore.catalog.dto.ReviewRequest;
import com.bookstore.catalog.dto.ReviewPageResponse;
import com.bookstore.catalog.entity.Review;
import com.bookstore.catalog.repository.BookRepository;
import com.bookstore.catalog.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReviewServiceTest {
    @Test
    void publicReviewSummaryContainsOnlyPublishedReviewFields() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        BookRepository books = mock(BookRepository.class);
        RestClient.Builder builder = RestClient.builder();
        ReviewService service = new ReviewService(reviews, books, builder, "http://orders");
        Review review = new Review();
        review.setBookId(19L);
        review.setCustomerEmail("private.reader@example.com");
        review.setReviewerName("A reader");
        review.setRating(5);
        review.setTitle("A lovely book");
        review.setComment("A useful and enjoyable read for a quiet weekend.");

        when(books.existsById(19L)).thenReturn(true);
        when(reviews.findByBookIdAndPublishedTrue(eq(19L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(review)));
        when(reviews.averageRating(19L)).thenReturn(5.0);
        when(reviews.countByBookIdAndPublishedTrue(19L)).thenReturn(1L);
        when(reviews.countByBookIdAndPublishedTrueAndRating(19L, 5)).thenReturn(1L);
        for (int stars = 1; stars <= 4; stars++) {
            when(reviews.countByBookIdAndPublishedTrueAndRating(19L, stars)).thenReturn(0L);
        }

        ReviewPageResponse response = service.list(19L, 0, 5, null, "newest", null);

        assertThat(response.averageRating()).isEqualTo(5.0);
        assertThat(response.reviewCount()).isEqualTo(1L);
        assertThat(response.reviews()).hasSize(1);
        assertThat(response.reviews().get(0).reviewerName()).isEqualTo("A reader");
        assertThat(response.reviews().get(0).verifiedPurchaser()).isTrue();
        assertThat(response.ratingDistribution()).containsEntry(5, 1L).containsEntry(4, 0L);
        verify(reviews, never()).findByBookIdAndCustomerEmail(eq(19L), any());
    }

    @Test
    void refusesReviewWhenOrderServiceDoesNotConfirmDelivery() {
        ReviewRepository reviews = mock(ReviewRepository.class);
        BookRepository books = mock(BookRepository.class);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ReviewService service = new ReviewService(reviews, books, builder, "http://orders");
        ReviewRequest request = new ReviewRequest();
        request.setRating(4);
        request.setTitle("Clear explanations");
        request.setComment("The chapters are clear and the practice problems are useful.");

        when(books.existsById(7L)).thenReturn(true);
        when(reviews.findByBookIdAndCustomerEmail(7L, "reader@example.com")).thenReturn(Optional.empty());
        server.expect(requestTo("http://orders/api/orders/me/review-eligibility/7"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess("{\"eligible\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.create(7L, "reader@example.com", "Reader", "Bearer test-token", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delivered order");

        server.verify();
        verify(reviews, never()).saveAndFlush(any());
    }
}
