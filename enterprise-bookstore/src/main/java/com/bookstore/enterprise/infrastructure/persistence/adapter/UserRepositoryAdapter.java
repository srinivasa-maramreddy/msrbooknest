package com.bookstore.enterprise.infrastructure.persistence.adapter;

import com.bookstore.enterprise.domain.model.AppUser;
import com.bookstore.enterprise.domain.repository.UserRepository;
import com.bookstore.enterprise.infrastructure.persistence.entity.JpaUserEntity;
import com.bookstore.enterprise.infrastructure.persistence.repository.JpaUserRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepositoryAdapter implements UserRepository {

    private final JpaUserRepository jpaUserRepository;

    public UserRepositoryAdapter(JpaUserRepository jpaUserRepository) {
        this.jpaUserRepository = jpaUserRepository;
    }

    @Override
    public Optional<AppUser> findById(Long id) {
        return jpaUserRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<AppUser> findByEmail(String email) {
        return jpaUserRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public Optional<AppUser> findByPhoneNumber(String phoneNumber) {
        return jpaUserRepository.findByPhoneNumber(phoneNumber).map(this::toDomain);
    }

    @Override
    public AppUser save(AppUser user) {
        JpaUserEntity entity = user.getId() == null
                ? new JpaUserEntity()
                : jpaUserRepository.findById(user.getId()).orElse(new JpaUserEntity());

        entity.setFullName(user.getFullName());
        entity.setEmail(user.getEmail());
        entity.setPhoneNumber(user.getPhoneNumber());
        entity.setPasswordHash(user.getPasswordHash());
        entity.setRole(user.getRole());
        entity.setEnabled(user.isEnabled());

        JpaUserEntity saved = jpaUserRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaUserRepository.existsByEmail(email);
    }

    @Override
    public boolean existsByPhoneNumber(String phoneNumber) {
        return jpaUserRepository.existsByPhoneNumber(phoneNumber);
    }

    private AppUser toDomain(JpaUserEntity entity) {
        return new AppUser(
                entity.getId(),
                entity.getFullName(),
                entity.getEmail(),
                entity.getPhoneNumber(),
                entity.getPasswordHash(),
                entity.getRole(),
                entity.isEnabled()
        );
    }
}
