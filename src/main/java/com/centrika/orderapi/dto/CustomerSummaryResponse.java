package com.centrika.orderapi.dto;

import java.math.BigDecimal;
import java.time.Instant;

// Constructed directly by the JPQL "new" expression in
// OrderItemRepository#summarizeForCustomer — field order must match.
public class CustomerSummaryResponse {
    private Long customerId;
    private BigDecimal totalSpend;
    private Long orderCount;
    private Instant lastOrderDate;

    public CustomerSummaryResponse(Long customerId, BigDecimal totalSpend, Long orderCount, Instant lastOrderDate) {
        this.customerId = customerId;
        this.totalSpend = totalSpend;
        this.orderCount = orderCount;
        this.lastOrderDate = lastOrderDate;
    }

    public Long getCustomerId() { return customerId; }
    public BigDecimal getTotalSpend() { return totalSpend; }
    public Long getOrderCount() { return orderCount; }
    public Instant getLastOrderDate() { return lastOrderDate; }
}
