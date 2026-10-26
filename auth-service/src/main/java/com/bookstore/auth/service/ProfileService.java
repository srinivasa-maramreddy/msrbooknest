package com.bookstore.auth.service;

import com.bookstore.auth.dto.ProfileResponse;
import com.bookstore.auth.dto.UpdateProfileRequest;
import com.bookstore.auth.entity.User;
import com.bookstore.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
@Transactional
public class ProfileService {
    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(String subject) {
        return response(ownedUser(subject));
    }

    public ProfileResponse update(String subject, UpdateProfileRequest request) {
        User user = ownedUser(subject);
        String name = request.name() == null ? "" : request.name().trim();
        if (name.isEmpty() || name.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your name using at most 100 characters.");
        }
        String phone = normalizePhone(request.phoneNumber());
        if (phone != null) {
            users.findByPhoneNumber(phone).filter(other -> !other.getId().equals(user.getId())).ifPresent(other -> {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "That mobile number is already used by another account.");
            });
        }
        String address = request.address() == null ? "" : request.address().trim();
        if (address.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Address must be at most 1000 characters.");
        }
        user.setName(name);
        user.setPhoneNumber(phone);
        user.setAddress(address.isEmpty() ? null : address);
        try {
            return response(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That mobile number is already used by another account.");
        }
    }

    private User ownedUser(String subject) {
        if (subject == null || subject.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in to view your profile.");
        }
        String identifier = subject.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(identifier).or(() -> users.findByPhoneNumber(identifier))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your account is unavailable. Please sign in again."));
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Your account is not verified.");
        }
        return user;
    }

    private String normalizePhone(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (!raw.matches("[+0-9()\\s-]+")) {
            throw invalidPhone();
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 12 && digits.startsWith("91")) digits = digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) digits = digits.substring(1);
        if (!digits.matches("[6-9][0-9]{9}")) throw invalidPhone();
        return digits;
    }

    private ResponseStatusException invalidPhone() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid 10-digit Indian mobile number or +91 format.");
    }

    private ProfileResponse response(User user) {
        return new ProfileResponse(user.getId(), user.getName(), user.getEmail(), user.getPhoneNumber(),
                user.getAddress(), user.getRole().name());
    }
}
