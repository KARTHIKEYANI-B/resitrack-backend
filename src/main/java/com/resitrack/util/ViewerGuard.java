package com.resitrack.util;

import com.resitrack.entity.Admin;
import com.resitrack.exception.CustomException;
import com.resitrack.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Shared guard used by every admin-facing controller write endpoint to
 * enforce the Viewer read-only constraint at the backend layer.
 *
 * Viewer accounts have ROLE_ADMIN (so they pass Spring Security's
 * hasRole('ADMIN') filter) but isViewer=true on their Admin row.
 * This guard is called at the start of every mutating endpoint handler
 * to throw 403 FORBIDDEN before any business logic runs.
 *
 * It is intentionally a separate injectable component rather than a
 * shared static helper so it can be injected wherever needed without
 * pulling in the full AdminRepository, and so future changes (e.g.
 * auditing viewer attempts) have a single point to extend.
 */
@Component
@RequiredArgsConstructor
public class ViewerGuard {

    private final AdminRepository adminRepo;

    /**
     * Throws {@link CustomException} with HTTP 403 if the authenticated
     * caller is a Viewer account. Should be called at the top of every
     * write (POST / PUT / DELETE / PATCH) endpoint in admin controllers.
     */
    public void rejectViewer(Authentication auth) {
        if (auth == null) return;
        Admin caller = adminRepo.findByEmail(auth.getName()).orElse(null);
        if (caller != null && caller.isViewer()) {
            throw new CustomException(
                    "Viewer accounts are read-only and cannot perform write operations.",
                    HttpStatus.FORBIDDEN);
        }
    }
}
