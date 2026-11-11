package kz.student.shop.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank(message = "Введите название категории")
        @Size(max = 100, message = "Название категории не должно превышать 100 символов")
        String name
) {
}
