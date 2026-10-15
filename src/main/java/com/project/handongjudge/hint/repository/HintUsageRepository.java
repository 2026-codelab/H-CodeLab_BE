package com.project.handongjudge.hint.repository;

import com.project.handongjudge.hint.entity.HintUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HintUsageRepository extends JpaRepository<HintUsage, Long> {
    Optional<HintUsage> findByUser_IdAndProblem_Id(Long userId, Long problemId);
}
