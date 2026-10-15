package com.project.handongjudge.hint.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HintResponseDTO {
    private String hint;
    private String category;
    private String tier;
    private int usedCount;
    private boolean usedFallback;
}
