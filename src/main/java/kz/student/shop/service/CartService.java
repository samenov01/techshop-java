package kz.student.shop.service;

import kz.student.shop.dto.*;
import kz.student.shop.exception.*;
import kz.student.shop.model.*;
import kz.student.shop.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Transactional(readOnly = true)
public class CartService {
    private final UserRepository users;
    private final ProductRepository products;
    private final CartItemRepository items;

    public CartService(UserRepository users, ProductRepository products, CartItemRepository items) {
        this.users = users;
        this.products = products;
        this.items = items;
    }

    public CartResponse get(String email) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return response(user.getId());
    }

    @Transactional
    public CartResponse add(String email, CartRequest request) {
        User user = lockUser(email);
        Product product = products.findById(request.productId()).orElseThrow(() -> new NotFoundException("Товар не найден"));
        CartItem item = items.findByUserIdAndProductId(user.getId(), product.getId()).orElseGet(CartItem::new);
        long quantity = (long) item.getQuantity() + request.quantity();
        if (quantity > product.getStock() || quantity > 10000) {
            throw new ConflictException("Недостаточно товара на складе или превышен лимит количества");
        }
        item.setUser(user);
        item.setProduct(product);
        item.setQuantity((int) quantity);
        items.saveAndFlush(item);
        return response(user.getId());
    }

    @Transactional
    public CartResponse update(String email, Long productId, int quantity) {
        User user = lockUser(email);
        CartItem item = items.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new NotFoundException("Товар не найден в корзине"));
        if (quantity > item.getProduct().getStock()) {
            throw new ConflictException("На складе осталось: " + item.getProduct().getStock());
        }
        item.setQuantity(quantity);
        items.flush();
        return response(user.getId());
    }

    @Transactional
    public CartResponse remove(String email, Long productId) {
        User user = lockUser(email);
        CartItem item = items.findByUserIdAndProductId(user.getId(), productId)
                .orElseThrow(() -> new NotFoundException("Товар не найден в корзине"));
        items.delete(item);
        items.flush();
        return response(user.getId());
    }

    private User lockUser(String email) {
        return users.lockByEmail(email).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
    }

    private CartResponse response(Long userId) {
        var rows = items.findByUserIdOrderByProductId(userId).stream().map(item -> {
            Product product = item.getProduct();
            return new CartResponse.Item(product.getId(), product.getName(), product.getCategory().getName(),
                    product.getPrice(), item.getQuantity(), product.getStock(),
                    product.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }).toList();
        BigDecimal total = rows.stream().map(CartResponse.Item::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(rows, total, rows.stream().mapToInt(CartResponse.Item::quantity).sum());
    }
}
