package kz.student.shop.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "Введите название товара")
        @Size(max = 150, message = "Название товара не должно превышать 150 символов")
        String name,

        @NotBlank(message = "Введите описание товара")
        @Size(max = 5000, message = "Описание не должно превышать 5000 символов")
        String description,

        @NotNull(message = "Укажите цену")
        @DecimalMin(value = "0.01", message = "Цена должна быть больше нуля")
        @Digits(integer = 10, fraction = 2, message = "Цена должна содержать до 10 цифр и до 2 знаков после запятой")
        BigDecimal price,

        @NotNull(message = "Укажите остаток")
        @Min(value = 0, message = "Остаток не может быть отрицательным")
        Integer stock,

        @NotNull(message = "Выберите категорию")
        @Positive(message = "Номер категории должен быть больше нуля")
        Long categoryId
) {
}
