package com.project.handongjudge.hint.controller;

import com.project.handongjudge.hint.dto.HintResponseDTO;
import com.project.handongjudge.hint.dto.HintUsageResponseDTO;
import com.project.handongjudge.hint.service.HintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class HintController {

    private final HintService hintService;

    /** 힌트 버튼을 처음 그릴 때 남은 횟수 표시용 (소모하지 않음). */
    @GetMapping("/{submissionDbId}/hint/usage")
    public ResponseEntity<HintUsageResponseDTO> getHintUsage(
            Authentication authentication,
            @PathVariable Long submissionDbId) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(hintService.getRemainingUses(userId, submissionDbId));
    }

    /** 학생이 "힌트" 버튼을 눌렀을 때 호출. 문제당 최대 3회까지만 허용(초과 시 HINT_LIMIT_EXCEEDED). */
    @PostMapping("/{submissionDbId}/hint")
    public ResponseEntity<HintResponseDTO> requestHint(
            Authentication authentication,
            @PathVariable Long submissionDbId) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(hintService.requestHint(userId, submissionDbId));
    }
}
