package com.bookstore.catalog.dto;

import java.util.List;
import java.util.Map;

public record ReviewPageResponse(double averageRating, long reviewCount, Map<Integer, Long> ratingDistribution,
                                 List<ReviewResponse> reviews, int page, int size, boolean hasMore,
                                 ReviewResponse myReview) {
}
