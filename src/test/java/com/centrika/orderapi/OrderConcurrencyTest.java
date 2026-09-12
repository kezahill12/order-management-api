package com.centrika.orderapi;

import com.centrika.orderapi.dto.OrderItemRequest;
import com.centrika.orderapi.dto.OrderRequest;
import com.centrika.orderapi.entity.Customer;
import com.centrika.orderapi.entity.CustomerTier;
import com.centrika.orderapi.entity.Product;
import com.centrika.orderapi.exception.InsufficientStockException;
import com.centrika.orderapi.repository.CustomerRepository;
import com.centrika.orderapi.repository.ProductRepository;
import com.centrika.orderapi.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

// Regression test for the Part 2 concurrency requirement: fires two
// concurrent requests at a product with stock = 1 and asserts exactly
// one succeeds. Uses the "test" profile pointed at H2 for speed; note
// H2's row-locking semantics differ slightly from Postgres, so this is
// a sanity check, not a substitute for testing against real Postgres
// before submission.
@SpringBootTest
@ActiveProfiles("test")
class OrderConcurrencyTest {

    @Autowired
    private OrderService orderService;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void twoConcurrentOrdersForLastUnitOfStock_onlyOneSucceeds() throws InterruptedException {
        Customer customer = new Customer();
        customer.setName("Test Customer");
        customer.setEmail("test-" + System.nanoTime() + "@example.com");
        customer.setRegion("EU");
        customer.setTier(CustomerTier.STANDARD);
        customer = customerRepository.save(customer);
        final Long customerId = customer.getId();

        Product product = new Product();
        product.setName("Limited Widget");
        product.setSku("SKU-" + System.nanoTime());
        product.setCategory("Widgets");
        product.setUnitPrice(new BigDecimal("19.99"));
        product.setStockQuantity(1); // only one unit available
        product = productRepository.save(product);
        final Long productId = product.getId();

        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Runnable task = () -> {
            OrderRequest request = new OrderRequest();
            request.setCustomerId(customerId);
            OrderItemRequest item = new OrderItemRequest();
            item.setProductId(productId);
            item.setQuantity(1);
            request.setItems(List.of(item));

            ready.countDown();
            try {
                go.await();
                orderService.createOrder(request);
                successes.incrementAndGet();
            } catch (InsufficientStockException e) {
                failures.incrementAndGet();
            } catch (InterruptedException ignored) {
            }
        };

        for (int i = 0; i < threadCount; i++) pool.submit(task);
        ready.await();
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS);

        assertThat(successes.get()).isEqualTo(1);
        assertThat(failures.get()).isEqualTo(1);

        Product reloaded = productRepository.findById(productId).orElseThrow();
        assertThat(reloaded.getStockQuantity()).isEqualTo(0);
    }
}
