package com.centrika.orderapi.dto;

import com.centrika.orderapi.entity.OrderStatus;

import java.time.Instant;
import java.util.List;

public class OrderResponse {
    private Long id;
    private Long customerId;
    private OrderStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private List<OrderItemResponse> items;

    public OrderResponse(Long id, Long customerId, OrderStatus status, Instant createdAt,
                          Instant updatedAt, List<OrderItemResponse> items) {
        this.id = id;
        this.customerId = customerId;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.items = items;
    }

    public Long getId() { return id; }
    public Long getCustomerId() { return customerId; }
    public OrderStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItemResponse> getItems() { return items; }
}
