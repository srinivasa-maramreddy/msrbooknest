package com.bookstore.auth.config;

import com.bookstore.auth.entity.Role;
import com.bookstore.auth.entity.User;
import com.bookstore.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoUserSeeder implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public DemoUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${app.demo.seed-users:true}") boolean enabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) return;
        createIfMissing("Reader Demo", "reader@msrbooknest.demo", "9000000001", "ReaderDemo@123", Role.CUSTOMER);
        createIfMissing("Store Admin", "admin@msrbooknest.demo", "9000000002", "AdminDemo@123", Role.ADMIN);
    }

    private void createIfMissing(String name, String email, String phone, String password, Role role) {
        if (userRepository.existsByEmail(email) || userRepository.existsByPhoneNumber(phone)) return;
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPhoneNumber(phone);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(true);
        userRepository.save(user);
    }
}
