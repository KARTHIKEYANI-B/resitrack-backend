package com.resitrack.repository;

import com.resitrack.entity.AppSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AppSettingsRepository extends JpaRepository<AppSettings, Long> {
    Optional<AppSettings> findFirstByOrderByIdAsc();
}
