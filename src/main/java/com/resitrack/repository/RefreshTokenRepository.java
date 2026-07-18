package com.resitrack.repository;

import com.resitrack.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** token here is always the SHA-256 hash — never the raw refresh token value. */
    Optional<RefreshToken> findByToken(String token);

    List<RefreshToken> findByUserIdAndRole(Long userId, String role);
}
