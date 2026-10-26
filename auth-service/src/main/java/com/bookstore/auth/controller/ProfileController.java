package com.bookstore.auth.controller;

import com.bookstore.auth.dto.ProfileResponse;
import com.bookstore.auth.dto.UpdateProfileRequest;
import com.bookstore.auth.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;

@CrossOrigin(origins = "${app.web.origins:http://localhost:5173}")
@RestController
@RequestMapping({"/api/auth/profile", "/auth/profile"})
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping
    public ProfileResponse get(Principal principal) {
        return profiles.get(principal.getName());
    }

    @PutMapping
    public ProfileResponse update(Principal principal, @Valid @RequestBody UpdateProfileRequest request) {
        return profiles.update(principal.getName(), request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> profileError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("error", error.getReason() == null ? "Could not load your profile." : error.getReason()));
    }
}
