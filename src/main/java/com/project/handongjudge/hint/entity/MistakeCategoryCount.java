package com.project.handongjudge.hint.entity;

import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

/** 학생이 카테고리별로 몇 번째 반복해서 틀리는지 누적 집계 (전 과목, 리셋 없음). 설계 문서 2장/4장 참고. */
@Entity
@Table(name = "mistake_category_counts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_mistake_count_user_category", columnNames = {"user_id", "category"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MistakeCategoryCount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private ErrorCategory category;

    @Column(name = "count", nullable = false)
    private int count;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
