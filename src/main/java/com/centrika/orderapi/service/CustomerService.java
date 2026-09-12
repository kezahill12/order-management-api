package com.centrika.orderapi.service;

import com.centrika.orderapi.dto.CustomerSummaryResponse;
import com.centrika.orderapi.exception.ResourceNotFoundException;
import com.centrika.orderapi.repository.CustomerRepository;
import com.centrika.orderapi.repository.OrderItemRepository;
import org.springframework.stereotype.Service;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final OrderItemRepository orderItemRepository;

    public CustomerService(CustomerRepository customerRepository, OrderItemRepository orderItemRepository) {
        this.customerRepository = customerRepository;
        this.orderItemRepository = orderItemRepository;
    }

    public CustomerSummaryResponse getSummary(Long customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer " + customerId + " not found");
        }
        return orderItemRepository.summarizeForCustomer(customerId)
                .orElseGet(() -> new CustomerSummaryResponse(customerId, java.math.BigDecimal.ZERO, 0L, null));
    }
}
