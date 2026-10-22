package com.bookstore.events;

public final class BookstoreTopics {
    public static final String PAYMENT_REQUESTED = "bookstore.orders.payment-requested.v1";
    public static final String PAYMENT_SUCCEEDED = "bookstore.payments.succeeded.v1";
    public static final String PAYMENT_FAILED = "bookstore.payments.failed.v1";
    public static final String ORDER_PAID = "bookstore.orders.paid.v1";
    public static final String ORDER_CANCELLED = "bookstore.orders.cancelled.v1";

    private BookstoreTopics() {
    }
}
