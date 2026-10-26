package com.bookstore.auth.service;

import com.bookstore.auth.dto.ProfileResponse;
import com.bookstore.auth.dto.UpdateProfileRequest;
import com.bookstore.auth.entity.Role;
import com.bookstore.auth.entity.User;
import com.bookstore.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileServiceTest {
    private UserRepository users;
    private ProfileService profiles;
    private User owner;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        profiles = new ProfileService(users);
        owner = new User();
        owner.setId(7L);
        owner.setName("Reader");
        owner.setEmail("reader@example.com");
        owner.setPhoneNumber("9876543210");
        owner.setRole(Role.CUSTOMER);
        owner.setPassword("unchanged-password-hash");
        when(users.findByEmail("reader@example.com")).thenReturn(Optional.of(owner));
        when(users.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void updateNormalizesMobileAndPersistsAddressWithoutChangingEmailOrPassword() {
        when(users.findByPhoneNumber("9123456789")).thenReturn(Optional.empty());
        ProfileResponse response = profiles.update("reader@example.com",
                new UpdateProfileRequest("  New Reader  ", "+91 91234-56789", "  21 Book Street, Hyderabad  "));
        assertEquals("New Reader", response.name());
        assertEquals("9123456789", response.phoneNumber());
        assertEquals("21 Book Street, Hyderabad", response.address());
        assertEquals("reader@example.com", response.email());
        assertEquals("unchanged-password-hash", owner.getPassword());
        verify(users).saveAndFlush(owner);
    }

    @Test
    void refusesAnotherAccountsMobileWithoutSavingProfile() {
        User other = new User();
        other.setId(8L);
        when(users.findByPhoneNumber("9123456789")).thenReturn(Optional.of(other));
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> profiles.update(
                "reader@example.com", new UpdateProfileRequest("Reader", "9123456789", "Address")));
        assertEquals(HttpStatus.CONFLICT, failure.getStatusCode());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDisabledAccountsEvenWithAValidTokenSubject() {
        owner.setEnabled(false);
        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> profiles.get("reader@example.com"));
        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
    }

    @Test
    void refusesInvalidMobileInsteadOfStrippingLetters() {
        ResponseStatusException failure = assertThrows(ResponseStatusException.class, () -> profiles.update(
                "reader@example.com", new UpdateProfileRequest("Reader", "abc9876543210", "Address")));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void existingMobileAndEmptyOptionalAddressCanBeSaved() {
        when(users.findByPhoneNumber("9876543210")).thenReturn(Optional.of(owner));
        ProfileResponse response = profiles.update("reader@example.com", new UpdateProfileRequest("Reader", "9876543210", " "));
        assertNull(response.address());
        assertEquals("9876543210", response.phoneNumber());
    }
}
