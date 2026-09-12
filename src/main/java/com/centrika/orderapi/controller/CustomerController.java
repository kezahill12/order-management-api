package com.centrika.orderapi.controller;

import com.centrika.orderapi.dto.CustomerSummaryResponse;
import com.centrika.orderapi.service.CustomerService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/{id}/summary")
    public CustomerSummaryResponse summary(@PathVariable Long id) {
        return customerService.getSummary(id);
    }
}
