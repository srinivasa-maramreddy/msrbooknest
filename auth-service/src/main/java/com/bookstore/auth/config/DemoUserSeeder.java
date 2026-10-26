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
    private final boolean readerEnabled;

    public DemoUserSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${app.demo.seed-users:true}") boolean enabled,
                          @Value("${app.demo.seed-reader:false}") boolean readerEnabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.readerEnabled = readerEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (enabled) {
            createIfMissing("Reader Demo", "reader@msrbooknest.demo", "9000000001", "ReaderDemo@123", Role.CUSTOMER);
            createIfMissing("Store Admin", "admin@msrbooknest.demo", "9000000002", "AdminDemo@123", Role.ADMIN);
        } else if (readerEnabled) {
            seedReaderOnly();
        }
    }

    private void seedReaderOnly() {
        User user = userRepository.findByEmail("reader@msrbooknest.demo").orElse(null);
        if (user == null) {
            if (userRepository.existsByPhoneNumber("9000000001")) return;
            user = new User();
            user.setName("Demo Reader");
            user.setEmail("reader@msrbooknest.demo");
            user.setPhoneNumber("9000000001");
            user.setRole(Role.CUSTOMER);
        } else if (user.getRole() != Role.CUSTOMER) {
            return;
        }

        user.setPassword(passwordEncoder.encode("ReaderDemo@123"));
        user.setEnabled(true);
        userRepository.save(user);
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
