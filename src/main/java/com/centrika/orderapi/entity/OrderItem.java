package com.centrika.orderapi.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    // Snapshot of Product.unitPrice at the moment the order was placed.
    // Never derived from Product at read time — see schema.sql comment.
    @Column(name = "unit_price_at_purchase", nullable = false)
    private BigDecimal unitPriceAtPurchase;

    public Long getId() { return id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getUnitPriceAtPurchase() { return unitPriceAtPurchase; }
    public void setUnitPriceAtPurchase(BigDecimal unitPriceAtPurchase) { this.unitPriceAtPurchase = unitPriceAtPurchase; }
}
