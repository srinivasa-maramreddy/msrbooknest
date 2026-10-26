package com.bookstore.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PaymentOrderDeadlineTest {
    private PaymentService service(String baseUrl, PaymentTransactionStore store) {
        return new PaymentService(null, null, store, null, null, RestClient.builder(),
                new ObjectMapper(), null, baseUrl, 100, 100);
    }

    @Test
    void unresponsiveOrderServiceFailsWithinReadDeadlineWithoutApplicationRetries() throws Exception {
        assertStalledOrderTimesOut(false);
    }

    @Test
    void stalledOrderResponseBodyAlsoFailsWithinReadDeadline() throws Exception {
        assertStalledOrderTimesOut(true);
    }

    private void assertStalledOrderTimesOut(boolean sendHeaders) throws Exception {
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        upstream.setExecutor(executor);
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch release = new CountDownLatch(1);
        upstream.createContext("/", exchange -> {
            calls.incrementAndGet();
            if (sendHeaders) {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, 1000);
                exchange.getResponseBody().write('{');
                exchange.getResponseBody().flush();
            }
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        upstream.start();
        try {
            long started = System.nanoTime();
            IllegalStateException failure = assertThrows(IllegalStateException.class, () ->
                    service("http://127.0.0.1:" + upstream.getAddress().getPort(), null)
                            .checkStatus("reader@example.test", "Bearer test-token", "order-1"));
            assertEquals("Could not verify your order with the order service", failure.getMessage());
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2000,
                    "A failed dependency must not leave a checkout request waiting indefinitely");
            assertTrue(hasCause(failure, SocketTimeoutException.class), "The bounded socket read should time out");
            assertEquals(1, calls.get());
        } finally {
            release.countDown();
            upstream.stop(0);
            executor.shutdownNow();
        }
    }

    @Test
    void validOrderLookupForwardsOwnershipTokenAndReturnsExpectedMissingPayment() throws Exception {
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        upstream.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            path.set(exchange.getRequestURI().getPath());
            byte[] body = ("{\"id\":1,\"orderRequestId\":\"order-1\",\"customerEmail\":\"reader@example.test\","
                    + "\"paymentMethod\":\"UPI\",\"status\":\"PENDING_PAYMENT\",\"totalAmount\":125.00}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
        try {
            PaymentTransactionStore noPayment = new PaymentTransactionStore(null, null) {
                @Override
                public com.bookstore.payment.entity.PaymentTransaction find(String orderRequestId) {
                    assertEquals("order-1", orderRequestId);
                    return null;
                }
            };
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () ->
                    service("http://127.0.0.1:" + upstream.getAddress().getPort(), noPayment)
                            .checkStatus("reader@example.test", "Bearer test-token", "order-1"));
            assertEquals("Payment was not created for this order", failure.getMessage());
            assertEquals("Bearer test-token", authorization.get());
            assertEquals("/api/orders/me/reference/order-1", path.get());
        } finally { upstream.stop(0); }
    }

    private boolean hasCause(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) return true;
        }
        return false;
    }
}
