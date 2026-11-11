package kz.student.shop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long id, LocalDateTime createdAt, String status, String paymentStatus,
                            BigDecimal totalAmount, String customerName, List<Item> items) {
    public record Item(Long productId, String name, int quantity, BigDecimal unitPrice) {
    }
}
