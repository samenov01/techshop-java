package kz.student.shop.exception;

import java.util.Map;

public record ApiError(String message, Map<String, String> errors) {
}
