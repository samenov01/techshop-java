package kz.student.shop.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Введите имя") @Size(max = 100) String name,
        @NotBlank(message = "Введите email") @Email(message = "Проверьте email") @Size(max = 255) String email,
        @NotBlank(message = "Введите пароль")
        @Size(min = 8, max = 60, message = "Пароль должен содержать от 8 до 60 символов") String password
) {
}
