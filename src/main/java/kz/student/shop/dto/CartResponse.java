package kz.student.shop.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<Item> items, BigDecimal total, int count) {
    public record Item(Long productId, String name, String categoryName, BigDecimal price,
                       int quantity, int stock, BigDecimal subtotal) {
    }
}
