package kz.student.shop.service;

import kz.student.shop.dto.OrderResponse;
import kz.student.shop.exception.*;
import kz.student.shop.model.*;
import kz.student.shop.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class OrderService {
    private final UserRepository users;
    private final ProductRepository products;
    private final CartItemRepository cart;
    private final OrderRepository orders;
    private final OrderItemRepository items;

    public OrderService(UserRepository users, ProductRepository products, CartItemRepository cart,
                        OrderRepository orders, OrderItemRepository items) {
        this.users = users;
        this.products = products;
        this.cart = cart;
        this.orders = orders;
        this.items = items;
    }

    @Transactional
    public OrderResponse checkout(String email) {
        User user = users.lockByEmail(email).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        List<CartItem> basket = cart.findByUserIdOrderByProductId(user.getId());
        if (basket.isEmpty()) {
            throw new ConflictException("Корзина пуста");
        }
        BigDecimal total = BigDecimal.ZERO;
        List<OrderItem> orderItems = new ArrayList<>();
        // Один порядок блокировки товаров предотвращает взаимную блокировку двух заказов.
        for (CartItem row : basket) {
            Product product = products.lockById(row.getProduct().getId()).orElseThrow(() -> new NotFoundException("Товар не найден"));
            if (!product.isActive()) {
                throw new ConflictException("Товар «" + product.getName() + "» удалён из каталога");
            }
            if (row.getQuantity() > product.getStock()) {
                throw new ConflictException("Недостаточно товара «" + product.getName() + "». Осталось: " + product.getStock());
            }
            product.setStock(product.getStock() - row.getQuantity());
            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setQuantity(row.getQuantity());
            item.setUnitPrice(product.getPrice());
            orderItems.add(item);
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(row.getQuantity())));
        }
        if (total.compareTo(new BigDecimal("9999999999.99")) > 0) {
            throw new ConflictException("Сумма заказа слишком большая");
        }
        Order order = new Order();
        order.setUser(user);
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus("NEW");
        order.setPaymentStatus("UNPAID");
        order.setTotalAmount(total);
        orders.save(order);
        for (OrderItem item : orderItems) {
            item.setOrder(order);
            items.save(item);
        }
        cart.deleteAll(basket);
        orders.flush();
        return response(order);
    }

    public List<OrderResponse> history(String email) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow(() -> new NotFoundException("Пользователь не найден"));
        return orders.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(this::response).toList();
    }

    public List<OrderResponse> all() {
        return orders.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @Transactional
    public OrderResponse pay(String email, Long id) {
        Order order = getLocked(id);
        requireOwner(order, email);
        if (!Set.of("NEW", "CONFIRMED").contains(order.getStatus())) {
            throw new ConflictException("Этот заказ уже нельзя оплатить");
        }
        if (order.getPaymentStatus().equals("UNPAID")) {
            order.setPaymentStatus("PAID");
        }
        return response(order);
    }

    @Transactional
    public OrderResponse cancel(String email, Long id) {
        Order order = getLocked(id);
        requireOwner(order, email);
        cancelOrder(order);
        return response(order);
    }

    @Transactional
    public OrderResponse changeStatus(Long id, String status) {
        Order order = getLocked(id);
        if (status.equals("CANCELLED")) {
            cancelOrder(order);
            return response(order);
        }
        Map<String, String> next = Map.of("NEW", "CONFIRMED", "CONFIRMED", "SHIPPED", "SHIPPED", "COMPLETED");
        if (!status.equals(next.get(order.getStatus()))) {
            throw new ConflictException("Недопустимый переход статуса заказа");
        }
        if (status.equals("SHIPPED") && !order.getPaymentStatus().equals("PAID")) {
            throw new ConflictException("Сначала нужно подтвердить оплату");
        }
        order.setStatus(status);
        return response(order);
    }

    private void cancelOrder(Order order) {
        if (order.getStatus().equals("CANCELLED")) {
            return;
        }
        if (!Set.of("NEW", "CONFIRMED").contains(order.getStatus())) {
            throw new ConflictException("После отправки отмена недоступна");
        }
        for (OrderItem item : items.findByOrderIdOrderByProductId(order.getId())) {
            Product product = products.lockById(item.getProduct().getId()).orElseThrow(() -> new NotFoundException("Товар не найден"));
            long stock = (long) product.getStock() + item.getQuantity();
            if (stock > Integer.MAX_VALUE) {
                throw new ConflictException("Остаток превышает допустимое значение");
            }
            product.setStock((int) stock);
        }
        order.setStatus("CANCELLED");
        if (order.getPaymentStatus().equals("PAID")) {
            order.setPaymentStatus("REFUNDED");
        }
    }

    private Order getLocked(Long id) {
        return orders.lockById(id).orElseThrow(() -> new NotFoundException("Заказ не найден"));
    }

    private void requireOwner(Order order, String email) {
        if (!order.getUser().getEmail().equalsIgnoreCase(email)) {
            throw new NotFoundException("Заказ не найден");
        }
    }

    private OrderResponse response(Order order) {
        var rows = items.findByOrderIdOrderByProductId(order.getId()).stream()
                .map(item -> new OrderResponse.Item(item.getProduct().getId(), item.getProductName(), item.getQuantity(), item.getUnitPrice())).toList();
        return new OrderResponse(order.getId(), order.getCreatedAt(), order.getStatus(), order.getPaymentStatus(),
                order.getTotalAmount(), order.getUser().getName(), rows);
    }
}
