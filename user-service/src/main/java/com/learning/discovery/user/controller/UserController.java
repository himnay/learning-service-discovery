package com.learning.discovery.user.controller;

import com.learning.discovery.user.model.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
class UserController {

    private static final List<User> USERS = List.of(
            new User(1L, "Alice", "alice@example.com"),
            new User(2L, "Bob", "bob@example.com"),
            new User(3L, "Carol", "carol@example.com"));

    @GetMapping("/api/users")
    List<User> getUsers() {
        return USERS;
    }

    @GetMapping("/api/users/{id}")
    User getUser(@PathVariable Long id) {
        return USERS.stream()
                .filter(user -> user.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + id + " not found"));
    }
}
