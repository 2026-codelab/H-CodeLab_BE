package com.project.handongjudge.section.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SectionRequest {
    private Long courseId;
    private Long instructorId;
    private Integer sectionNumber;
    private Integer year;
    private String semester;
    private String language;  // 수업 언어: c, cpp, java, python (미지정 시 c)
}
