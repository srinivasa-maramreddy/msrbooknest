package com.bookstore.auth.controller;

import com.bookstore.auth.config.SecurityConfig;
import com.bookstore.auth.dto.ProfileResponse;
import com.bookstore.auth.service.ProfileService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ProfileController.class, properties = "app.jwt.secret=profile-test-secret-is-long-enough-for-hs256")
@Import(SecurityConfig.class)
class ProfileSecurityTest {
    private static final String SECRET = "profile-test-secret-is-long-enough-for-hs256";
    @Autowired private MockMvc mvc;
    @MockBean private ProfileService profiles;

    @Test
    void profileRequiresSignedBearerToken() throws Exception {
        mvc.perform(get("/api/auth/profile")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/profile").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void derivesOwnerFromSignedSubjectAndReturnsSafeProfileFields() throws Exception {
        when(profiles.get("owner@example.com")).thenReturn(new ProfileResponse(3L, "Owner", "owner@example.com", "9876543210", "Home", "CUSTOMER"));
        mvc.perform(get("/api/auth/profile").param("email", "other@example.com").header("Authorization", "Bearer " + token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.address").value("Home"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());
        verify(profiles).get("owner@example.com");
    }

    @Test
    void emptyNameCannotReachPersistence() throws Exception {
        mvc.perform(put("/api/auth/profile").header("Authorization", "Bearer " + token())
                        .contentType("application/json").content("{\"name\":\"\",\"phoneNumber\":\"9876543210\",\"address\":\"Home\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }

    private String token() {
        return Jwts.builder().subject("owner@example.com").expiration(Date.from(Instant.now().plusSeconds(120)))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
    }
}
