package com.bookstore.enterprise.infrastructure.persistence.adapter;

import com.bookstore.enterprise.domain.model.BookOrder;
import com.bookstore.enterprise.domain.model.OrderItem;
import com.bookstore.enterprise.domain.repository.OrderRepository;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaBookEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaOrderEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaOrderItemEntity;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaUserEntity;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaBookRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaOrderRepository;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaUserRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class OrderRepositoryAdapter implements OrderRepository {

    private final JpaOrderRepository jpaOrderRepository;
    private final JpaUserRepository jpaUserRepository;
    private final JpaBookRepository jpaBookRepository;

    public OrderRepositoryAdapter(
            JpaOrderRepository jpaOrderRepository,
            JpaUserRepository jpaUserRepository,
            JpaBookRepository jpaBookRepository
    ) {
        this.jpaOrderRepository = jpaOrderRepository;
        this.jpaUserRepository = jpaUserRepository;
        this.jpaBookRepository = jpaBookRepository;
    }

    @Override
    public BookOrder save(BookOrder order) {
        JpaOrderEntity entity = order.getId() == null
                ? new JpaOrderEntity()
                : jpaOrderRepository.findById(order.getId()).orElse(new JpaOrderEntity());

        JpaUserEntity userEntity = jpaUserRepository.findById(order.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found for order"));

        entity.setUser(userEntity);
        entity.setStatus(order.getStatus());
        entity.setPaymentMethod(order.getPaymentMethod());
        entity.setShippingAddress(order.getShippingAddress());
        entity.setTotalAmount(order.getTotalAmount());

        entity.getItems().clear();
        for (OrderItem item : order.getItems()) {
            JpaBookEntity bookEntity = jpaBookRepository.findById(item.getBookId())
                    .orElseThrow(() -> new IllegalArgumentException("Book not found for order item"));

            JpaOrderItemEntity itemEntity = new JpaOrderItemEntity();
            itemEntity.setOrder(entity);
            itemEntity.setBook(bookEntity);
            itemEntity.setQuantity(item.getQuantity());
            itemEntity.setUnitPrice(item.getUnitPrice());
            itemEntity.setLineTotal(item.getLineTotal());
            entity.getItems().add(itemEntity);
        }

        JpaOrderEntity saved = jpaOrderRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<BookOrder> findById(Long id) {
        return jpaOrderRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<BookOrder> findByUserId(Long userId) {
        return jpaOrderRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public List<BookOrder> findAll() {
        return jpaOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private BookOrder toDomain(JpaOrderEntity entity) {
        BookOrder order = new BookOrder();
        order.setId(entity.getId());
        order.setUserId(entity.getUser().getId());
        order.setStatus(entity.getStatus());
        order.setPaymentMethod(entity.getPaymentMethod());
        order.setTotalAmount(entity.getTotalAmount());
        order.setShippingAddress(entity.getShippingAddress());
        order.setCreatedAt(entity.getCreatedAt());

        List<OrderItem> items = entity.getItems().stream()
                .map(itemEntity -> new OrderItem(
                        itemEntity.getId(),
                        itemEntity.getBook().getId(),
                        itemEntity.getQuantity(),
                        itemEntity.getUnitPrice(),
                        itemEntity.getLineTotal()
                ))
                .collect(Collectors.toList());
        order.setItems(items);

        return order;
    }
}
