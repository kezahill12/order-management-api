package com.centrika.orderapi.dto;

import java.math.BigDecimal;

public class OrderItemResponse {
    private Long productId;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPriceAtPurchase;

    public OrderItemResponse(Long productId, String productName, Integer quantity, BigDecimal unitPriceAtPurchase) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPriceAtPurchase = unitPriceAtPurchase;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public BigDecimal getUnitPriceAtPurchase() { return unitPriceAtPurchase; }
}
