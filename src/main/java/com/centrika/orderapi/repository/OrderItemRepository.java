package com.centrika.orderapi.repository;

import com.centrika.orderapi.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    // Powers GET /api/customers/{id}/summary: total spend, order count,
    // last order date, all in a single round trip instead of pulling
    // every order/order_item into memory.
    @Query("""
        SELECT new com.centrika.orderapi.dto.CustomerSummaryResponse(
            :customerId,
            COALESCE(SUM(oi.quantity * oi.unitPriceAtPurchase), 0),
            COUNT(DISTINCT o.id),
            MAX(o.createdAt))
        FROM Order o
        LEFT JOIN o.items oi
        WHERE o.customer.id = :customerId
        """)
    Optional<com.centrika.orderapi.dto.CustomerSummaryResponse> summarizeForCustomer(@Param("customerId") Long customerId);
}
