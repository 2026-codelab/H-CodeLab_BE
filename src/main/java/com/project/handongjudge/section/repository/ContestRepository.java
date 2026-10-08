package com.project.handongjudge.section.repository;

import com.project.handongjudge.section.entity.Contest;
import com.project.handongjudge.section.entity.Section;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ContestRepository extends JpaRepository<Contest, Long> {
    Optional<Contest> findBySection(Section section);

    /** 분반 삭제 시 FK 제약 회피: 해당 분반을 참조하는 contest 삭제 */
    void deleteBySection_Id(Long sectionId);
}
