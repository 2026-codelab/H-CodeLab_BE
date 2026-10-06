package com.project.handongjudge.user.entity;

import com.project.handongjudge.section.entity.Section;
import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment {

    /**
     * 교수가 자기 수업에서 코드 실행·제출을 해볼 수 있도록 만든 등록(DOMjudge 팀 보유).
     * 학생 목록·수강생 수·성적·진도·학생 알림에서는 제외한다.
     * (JPQL 쿼리에서는 문자열 'INSTRUCTOR'로 직접 비교)
     */
    public static final String ROLE_INSTRUCTOR = "INSTRUCTOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id")
    private Section section;


    @Column(name = "team_id", nullable = true)  
    private String teamId;

    private String roleInCourse;

    private LocalDateTime joinedAt;
}
