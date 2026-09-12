package com.centrika.orderapi.repository;

import com.centrika.orderapi.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    // Pessimistic write lock: the row is locked ("SELECT ... FOR UPDATE")
    // for the duration of the transaction, so a second concurrent request
    // for the same product blocks until the first commits or rolls back.
    // See DESIGN.md for why this was chosen over pure optimistic locking
    // for the stock-deduction path specifically.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);
}
