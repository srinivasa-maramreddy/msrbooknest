package com.bookstore.enterprise.application.service.impl;

import com.bookstore.enterprise.application.dto.order.CreateOrderRequest;
import com.bookstore.enterprise.application.dto.order.OrderItemRequest;
import com.bookstore.enterprise.application.dto.order.OrderResponse;
import com.bookstore.enterprise.application.exception.BusinessValidationException;
import com.bookstore.enterprise.application.exception.ResourceNotFoundException;
import com.bookstore.enterprise.application.mapper.OrderMapper;
import com.bookstore.enterprise.application.payment.PaymentProcessor;
import com.bookstore.enterprise.application.payment.PaymentResult;
import com.bookstore.enterprise.application.service.OrderService;
import com.bookstore.enterprise.domain.model.AppUser;
import com.bookstore.enterprise.domain.model.Book;
import com.bookstore.enterprise.domain.model.BookOrder;
import com.bookstore.enterprise.domain.model.OrderItem;
import com.bookstore.enterprise.domain.model.OrderStatus;
import com.bookstore.enterprise.domain.repository.BookRepository;
import com.bookstore.enterprise.domain.repository.OrderRepository;
import com.bookstore.enterprise.domain.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final OrderMapper orderMapper;
    private final PaymentProcessor paymentProcessor;

    public OrderServiceImpl(
            OrderRepository orderRepository,
            UserRepository userRepository,
            BookRepository bookRepository,
            OrderMapper orderMapper,
            PaymentProcessor paymentProcessor
    ) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.bookRepository = bookRepository;
        this.orderMapper = orderMapper;
        this.paymentProcessor = paymentProcessor;
    }

    @Override
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String userEmail) {
        AppUser user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email " + userEmail));

        List<OrderItem> orderItems = new ArrayList<>();
        Map<Long, String> bookTitlesById = new HashMap<>();

        for (OrderItemRequest itemRequest : request.getItems()) {
            Book book = bookRepository.findById(itemRequest.getBookId())
                    .orElseThrow(() -> new ResourceNotFoundException("Book not found with id " + itemRequest.getBookId()));

            if (!book.hasInventory(itemRequest.getQuantity())) {
                throw new BusinessValidationException("Insufficient stock for book id " + book.getId());
            }

            book.reduceStock(itemRequest.getQuantity());
            bookRepository.save(book);

            OrderItem orderItem = new OrderItem();
            orderItem.setBookId(book.getId());
            orderItem.setQuantity(itemRequest.getQuantity());
            orderItem.setUnitPrice(book.getPrice());
            orderItem.calculateLineTotal();
            orderItems.add(orderItem);
            bookTitlesById.put(book.getId(), book.getTitle());
        }

        BookOrder order = new BookOrder();
        order.setUserId(user.getId());
        order.setStatus(OrderStatus.CREATED);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setShippingAddress(request.getShippingAddress());
        order.setItems(orderItems);
        order.setCreatedAt(LocalDateTime.now());
        order.calculateTotalAmount();

        PaymentResult paymentResult = paymentProcessor.charge(
                request.getPaymentMethod(),
                order.getTotalAmount(),
                "order-" + user.getId() + "-" + System.currentTimeMillis()
        );
        if (!paymentResult.isSuccess()) {
            throw new BusinessValidationException("Payment failed: " + paymentResult.getMessage());
        }
        order.setStatus(OrderStatus.PAID);

        BookOrder savedOrder = orderRepository.save(order);
        log.info(
                "Created order id={} for userId={} total={} transactionId={}",
                savedOrder.getId(),
                user.getId(),
                savedOrder.getTotalAmount(),
                paymentResult.getTransactionId()
        );

        return orderMapper.toResponse(savedOrder, bookTitlesById);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getOrdersForCurrentUser(String userEmail) {
        AppUser user = userRepository.findByEmail(userEmail.toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("User not found for email " + userEmail));

        List<BookOrder> orders = orderRepository.findByUserId(user.getId());
        Map<Long, String> titles = resolveBookTitles(orders);
        return orders.stream()
                .map(order -> orderMapper.toResponse(order, titles))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        List<BookOrder> orders = orderRepository.findAll();
        Map<Long, String> titles = resolveBookTitles(orders);
        return orders.stream()
                .map(order -> orderMapper.toResponse(order, titles))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(Long orderId, OrderStatus status) {
        BookOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id " + orderId));

        order.setStatus(status);
        BookOrder updatedOrder = orderRepository.save(order);
        Map<Long, String> titles = resolveBookTitles(List.of(updatedOrder));
        log.info("Updated order id={} status={}", orderId, status);
        return orderMapper.toResponse(updatedOrder, titles);
    }

    private Map<Long, String> resolveBookTitles(List<BookOrder> orders) {
        Set<Long> bookIds = orders.stream()
                .flatMap(order -> order.getItems().stream())
                .map(OrderItem::getBookId)
                .collect(Collectors.toSet());

        Map<Long, String> titles = new HashMap<>();
        for (Long bookId : bookIds) {
            bookRepository.findById(bookId).ifPresent(book -> titles.put(bookId, book.getTitle()));
        }
        return titles;
    }
}
