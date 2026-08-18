package com.platform.exceptions.repository;

import com.platform.exceptions.domain.ExceptionNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExceptionNoteRepository extends JpaRepository<ExceptionNote, UUID> {
    List<ExceptionNote> findByExceptionIdOrderByCreatedAtAsc(UUID exceptionId);
}
