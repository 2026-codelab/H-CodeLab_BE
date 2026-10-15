package com.project.handongjudge.hint.repository;

import com.project.handongjudge.hint.entity.ErrorCategory;
import com.project.handongjudge.hint.entity.MistakeCategoryCount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MistakeCategoryCountRepository extends JpaRepository<MistakeCategoryCount, Long> {
    Optional<MistakeCategoryCount> findByUser_IdAndCategory(Long userId, ErrorCategory category);
}
