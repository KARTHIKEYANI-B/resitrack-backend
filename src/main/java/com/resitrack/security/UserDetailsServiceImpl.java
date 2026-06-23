package com.resitrack.security;

import com.resitrack.entity.Admin;
import com.resitrack.entity.Resident;
import com.resitrack.entity.SecurityGuard;
import com.resitrack.repository.AdminRepository;
import com.resitrack.repository.ResidentRepository;
import com.resitrack.repository.SecurityGuardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AdminRepository         adminRepository;
    private final ResidentRepository      residentRepository;
    private final SecurityGuardRepository securityGuardRepository;

    /**
     * Called by JwtAuthenticationFilter on every authenticated request.
     * The username is always an email address (the JWT subject).
     * We check in the same priority order as unifiedLogin:
     *   Admin → Security → Resident
     */
    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        // 1. Admin
        Admin admin = adminRepository.findByEmail(email).orElse(null);
        if (admin != null) {
            return User.builder()
                    .username(admin.getEmail())
                    .password(admin.getPassword())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .build();
        }

        // 2. Security guard
        SecurityGuard guard = securityGuardRepository.findByEmail(email).orElse(null);
        if (guard != null) {
            return User.builder()
                    .username(guard.getEmail())
                    .password(guard.getPassword())
                    .authorities(List.of(new SimpleGrantedAuthority("ROLE_SECURITY")))
                    .build();
        }

        // 3. Resident / Family member
        Resident resident = residentRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return User.builder()
                .username(resident.getEmail())
                .password(resident.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_USER")))
                .build();
    }
}