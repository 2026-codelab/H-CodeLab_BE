package com.project.handongjudge.progress.repository;

import com.project.handongjudge.progress.entity.CodeProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CodeProgressRepository extends JpaRepository<CodeProgress, Long> {
    Optional<CodeProgress> findByUserIdAndProblemIdAndSectionIdAndLanguage(
        Long userId,
        Long problemId,
        Long sectionId,
        String language
    );

    boolean existsByUserIdAndProblemIdAndSectionIdAndLanguage(
        Long userId,
        Long problemId,
        Long sectionId,
        String language
    );

    /** 분반 삭제 시 FK 제약 회피: 해당 분반을 참조하는 코드 진행 상황 삭제 */
    void deleteBySection_Id(Long sectionId);
}
