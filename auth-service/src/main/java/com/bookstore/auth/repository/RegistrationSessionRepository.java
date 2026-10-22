package com.bookstore.auth.repository;

import com.bookstore.auth.entity.RegistrationSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationSessionRepository extends JpaRepository<RegistrationSession, String> {
    void deleteByPhoneNumberAndCompletedFalse(String phoneNumber);
}
