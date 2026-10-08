package com.project.handongjudge.hint.entity;

import com.project.handongjudge.problem.entity.Problem;
import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

/** 문제당 힌트 사용 횟수 (최대 3회). 문제가 바뀌면 새 행이 생기므로 자연히 리셋됨. */
@Entity
@Table(name = "hint_usages", uniqueConstraints = {
        @UniqueConstraint(name = "uk_hint_usage_user_problem", columnNames = {"user_id", "problem_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HintUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
