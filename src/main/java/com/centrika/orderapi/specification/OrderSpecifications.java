package com.centrika.orderapi.specification;

import com.centrika.orderapi.entity.Order;
import com.centrika.orderapi.entity.OrderStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

// Building the WHERE clause as a Specification (rather than several
// hand-written @Query methods, one per filter combination) means every
// filter is applied server-side, combined with AND, and only the
// matching rows are ever pulled from the DB — nothing is paged through
// in application memory.
public class OrderSpecifications {

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasCustomerId(Long customerId) {
        return (root, query, cb) -> customerId == null ? null : cb.equal(root.get("customer").get("id"), customerId);
    }

    public static Specification<Order> createdFrom(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Order> createdTo(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }
}
