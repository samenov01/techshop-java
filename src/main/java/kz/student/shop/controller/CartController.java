package kz.student.shop.controller;

import jakarta.validation.Valid;
import kz.student.shop.dto.*;
import kz.student.shop.service.CartService;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping
    public CartResponse get(Principal user) {
        return cart.get(user.getName());
    }

    @PostMapping
    public CartResponse add(Principal user, @Valid @RequestBody CartRequest request) {
        return cart.add(user.getName(), request);
    }

    @PutMapping("/{productId}")
    public CartResponse update(Principal user, @PathVariable Long productId, @Valid @RequestBody QuantityRequest request) {
        return cart.update(user.getName(), productId, request.quantity());
    }

    @DeleteMapping("/{productId}")
    public CartResponse remove(Principal user, @PathVariable Long productId) {
        return cart.remove(user.getName(), productId);
    }
}
