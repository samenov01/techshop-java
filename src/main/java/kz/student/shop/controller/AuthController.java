package kz.student.shop.controller;

import jakarta.validation.Valid;
import kz.student.shop.dto.*;
import kz.student.shop.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserService users;

    public AuthController(UserService users) {
        this.users = users;
    }

    @GetMapping("/csrf")
    public CsrfToken csrf(CsrfToken token) {
        return token;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return users.register(request);
    }

    @GetMapping("/me")
    public UserResponse current(Principal principal) {
        return users.current(principal.getName());
    }
}
