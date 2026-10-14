package com.project.handongjudge.hint.entity;

import com.project.handongjudge.problem.entity.Problem;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 검증 게이트 시도 로그. 설계 문서 5장 "유출률 측정 계획"의 로그 스키마를 따름.
 * 테스트하기는 Submission을 DB에 저장하지 않으므로 problem + testcaseRank로 식별한다.
 * (submission_id 대신) problem_id, testcase_rank, 시도_번호, 정확성_게이트_결과, 유출_게이트_결과, 최종_결과
 */
@Entity
@Table(name = "hint_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HintLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    /** CE처럼 특정 테스트케이스가 없는 경우 null */
    @Column(name = "testcase_rank")
    private Integer testcaseRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 40)
    private ErrorCategory category;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "accuracy_gate_result", length = 10)
    private GateVerdict accuracyGateResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "leak_gate_result", length = 10)
    private GateVerdict leakGateResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "final_result", length = 10)
    private FinalResult finalResult;

    /** 이번 시도에서 생성된 힌트 원문. 유출 게이트 정확도를 사람이 직접 검수하려면 텍스트가 있어야 한다. */
    @Lob
    @Column(name = "hint_text")
    private String hintText;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum GateVerdict { PASS, FAIL }

    /** EXPOSED: 게이트를 통과해 학생에게 노출됨 / FALLBACK: 안전 템플릿으로 대체됨 */
    public enum FinalResult { EXPOSED, FALLBACK }
}
