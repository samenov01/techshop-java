package kz.student.shop.controller;

import kz.student.shop.dto.OrderResponse;
import kz.student.shop.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;

    public OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    public List<OrderResponse> history(Principal user) {
        return orders.history(user.getName());
    }

    @PostMapping
    public ResponseEntity<OrderResponse> checkout(Principal user) {
        OrderResponse order = orders.checkout(user.getName());
        return ResponseEntity.created(URI.create("/api/orders")).body(order);
    }

    @PostMapping("/{id}/pay")
    public OrderResponse pay(Principal user, @PathVariable Long id) {
        return orders.pay(user.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(Principal user, @PathVariable Long id) {
        return orders.cancel(user.getName(), id);
    }
}
