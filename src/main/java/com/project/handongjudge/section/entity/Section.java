// 1. Section 엔티티 수정 - semester를 String으로 변경
// Handongjudge_BE/src/main/java/com/project/handongjudge/section/entity/Section.java

package com.project.handongjudge.section.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.project.handongjudge.course.entity.Course;
import com.project.handongjudge.user.entity.User;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id")
    private Course course;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private User instructor;

    // 수업 생성자 (새로운 권한 시스템에서 사용)
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @Column(name = "section_number", nullable = true)
    private Integer sectionNumber;

    @Column(name = "enrollment_code", length = 50, unique = true)
    private String enrollmentCode;  // 수업 참가 코드

    // 추가 필드
    @Column(name = "year")
    private Integer year;  // 년도 (예: 2024)

    @Column(name = "semester", length = 10)
    private String semester;  // 학기: SPRING(1학기), SUMMER(여름학기), FALL(2학기), WINTER(겨울학기)

    // 수업에서 사용할 프로그래밍 언어 (수업 생성·복사 시 선택). 기존 수업(null)은 C로 취급
    @Column(name = "language", length = 20)
    private String language;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 활성화/비활성화 필드 추가
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;

    // CASCADE 관계 설정 - Section 삭제 시 함께 삭제될 엔티티들
    @JsonIgnore
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<com.project.handongjudge.assignment.entity.Assignment> assignments = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<com.project.handongjudge.notice.entity.Notice> notices = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<com.project.handongjudge.community.entity.Question> questions = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<com.project.handongjudge.user.entity.Enrollment> enrollments = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<com.project.handongjudge.community.entity.UserNickname> userNicknames = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<com.project.handongjudge.submission.entity.Submission> submissions = new ArrayList<>();

    // 수업별 사용자 역할 관계
    @JsonIgnore
    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<SectionUserRole> sectionUserRoles = new ArrayList<>();

    /** 수업 언어로 선택할 수 있는 언어 키 (FE·DOMjudge 제출에서 쓰는 값과 동일) */
    public static final List<String> SUPPORTED_LANGUAGES = List.of("c", "cpp", "java", "python");
    public static final String DEFAULT_LANGUAGE = "c";

    /** 언어 미지정(null/빈 값)은 C, 지원하지 않는 언어는 예외 */
    public static String normalizeLanguage(String language) {
        if (language == null || language.trim().isEmpty()) {
            return DEFAULT_LANGUAGE;
        }
        String normalized = language.trim().toLowerCase();
        if (!SUPPORTED_LANGUAGES.contains(normalized)) {
            throw new IllegalArgumentException("지원하지 않는 언어입니다: " + language);
        }
        return normalized;
    }

    /** 언어 컬럼이 비어 있는 기존 수업은 C로 취급 */
    public String getLanguageOrDefault() {
        return (language == null || language.trim().isEmpty()) ? DEFAULT_LANGUAGE : language;
    }

    /** 제출 언어가 수업 언어와 다르면 예외 */
    public void validateSubmissionLanguage(String submissionLanguage) {
        if (submissionLanguage == null || !getLanguageOrDefault().equals(submissionLanguage.trim().toLowerCase())) {
            throw new IllegalArgumentException(
                    "이 수업은 " + getLanguageOrDefault() + " 언어로만 제출할 수 있습니다. (요청 언어: " + submissionLanguage + ")");
        }
    }
}