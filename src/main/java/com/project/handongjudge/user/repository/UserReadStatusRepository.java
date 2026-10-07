package com.project.handongjudge.user.repository;

import com.project.handongjudge.user.entity.UserReadStatus;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserReadStatusRepository extends CrudRepository<UserReadStatus, Long> {
    boolean existsByUserIdAndNoticeId(Long userId, Long noticeId);
    boolean existsByUserIdAndAssignmentId(Long userId, Long assignmentId);

    /** 분반 삭제 시 FK 제약 회피: 해당 과제들을 참조하는 읽음 기록 삭제 */
    void deleteByAssignment_IdIn(List<Long> assignmentIds);

    /** 분반 삭제 시 FK 제약 회피: 해당 공지들을 참조하는 읽음 기록 삭제 */
    void deleteByNotice_IdIn(List<Long> noticeIds);
}