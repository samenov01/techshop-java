package kz.student.shop.controller;

import jakarta.validation.Valid;
import kz.student.shop.dto.*;
import kz.student.shop.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final OrderService orders;
    private final StatisticsService statistics;

    public AdminController(OrderService orders, StatisticsService statistics) {
        this.orders = orders;
        this.statistics = statistics;
    }

    @GetMapping("/orders")
    public List<OrderResponse> orders() {
        return orders.all();
    }

    @PutMapping("/orders/{id}/status")
    public OrderResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return orders.changeStatus(id, request.status());
    }

    @GetMapping("/statistics")
    public StatisticsService.Summary statistics() {
        return statistics.get();
    }
}
