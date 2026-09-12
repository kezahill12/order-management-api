package com.centrika.orderapi.service;

import com.centrika.orderapi.dto.*;
import com.centrika.orderapi.entity.*;
import com.centrika.orderapi.exception.InsufficientStockException;
import com.centrika.orderapi.exception.ResourceNotFoundException;
import com.centrika.orderapi.repository.*;
import com.centrika.orderapi.specification.OrderSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository, CustomerRepository customerRepository,
                         ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    public Page<OrderResponse> findOrders(OrderStatus status, Long customerId, Instant from, Instant to, Pageable pageable) {
        Specification<Order> spec = Specification
                .where(OrderSpecifications.hasStatus(status))
                .and(OrderSpecifications.hasCustomerId(customerId))
                .and(OrderSpecifications.createdFrom(from))
                .and(OrderSpecifications.createdTo(to));

        // Page/size come straight from the controller as a Spring Pageable,
        // which the repository translates into LIMIT/OFFSET at the DB level
        // — rows never leave Postgres beyond the requested page.
        return orderRepository.findAll(spec, pageable).map(this::toResponse);
    }

    public OrderResponse findById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " not found"));
        return toResponse(order);
    }

    // REQUIRES_NEW isn't used here on purpose: we want the stock deduction
    // and the order/order_item insert to commit or roll back together, as
    // one atomic unit. If either fails, nothing is persisted.
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer " + request.getCustomerId() + " not found"));

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus(OrderStatus.PENDING);

        for (OrderItemRequest itemReq : request.getItems()) {
            // Pessimistic write lock on the product row: this is what
            // actually prevents two concurrent orders from both succeeding
            // against the same low-stock product. Without it, two
            // transactions could both read stockQuantity=1, both decide
            // "enough stock", and both commit — overselling by one unit.
            // With FOR UPDATE, the second transaction blocks here until
            // the first commits (releasing the lock with the updated
            // stock), then re-reads the now-correct value.
            Product product = productRepository.findByIdForUpdate(itemReq.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product " + itemReq.getProductId() + " not found"));

            if (product.getStockQuantity() < itemReq.getQuantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product " + product.getSku() +
                        " (requested " + itemReq.getQuantity() + ", available " + product.getStockQuantity() + ")");
            }

            product.setStockQuantity(product.getStockQuantity() - itemReq.getQuantity());

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemReq.getQuantity());
            item.setUnitPriceAtPurchase(product.getUnitPrice());
            order.addItem(item);
        }

        Order saved = orderRepository.save(order);
        return toResponse(saved);
    }

    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order " + id + " not found"));
        order.setStatus(newStatus);
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(i -> new OrderItemResponse(i.getProduct().getId(), i.getProduct().getName(),
                        i.getQuantity(), i.getUnitPriceAtPurchase()))
                .toList();
        return new OrderResponse(order.getId(), order.getCustomer().getId(), order.getStatus(),
                order.getCreatedAt(), order.getUpdatedAt(), items);
    }
}
