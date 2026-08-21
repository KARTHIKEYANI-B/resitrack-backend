package com.resitrack.repository;

import com.resitrack.entity.PersonalReminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PersonalReminderRepository extends JpaRepository<PersonalReminder, Long> {

    List<PersonalReminder> findByResidentIdAndActiveTrueOrderByReminderDateAsc(Long residentId);

    Optional<PersonalReminder> findByIdAndResidentId(Long id, Long residentId);

    List<PersonalReminder> findByResidentIdAndReminderDateAndActiveTrueAndCompletedFalse(
            Long residentId, LocalDate reminderDate);
}
