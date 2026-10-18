package com.learning.discovery.user.controller;

import com.learning.discovery.user.model.User;
import com.learning.discovery.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Tag(name = "Users", description = "Read-only user directory (Postgres, seeded by Flyway)")
@ApiResponse(responseCode = "401", description = "Missing or wrong HTTP Basic credentials", content = @Content)
@RestController
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "List all users")
    @ApiResponse(responseCode = "200", description = "All users")
    @GetMapping("/api/users")
    List<User> getUsers() {
        return userService.findAll();
    }

    @Operation(summary = "Get a user by id")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "No user with this id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/users/{id}")
    User getUser(@Parameter(description = "User id", example = "1") @PathVariable Long id) {
        return userService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User " + id + " not found"));
    }
}
