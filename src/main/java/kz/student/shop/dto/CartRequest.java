package kz.student.shop.dto;

import jakarta.validation.constraints.*;

public record CartRequest(
        @NotNull @Positive Long productId,
        @NotNull @Min(value = 1, message = "Количество должно быть больше нуля")
        @Max(value = 10000, message = "Максимум 10000 единиц в одной позиции") Integer quantity
) {
}
