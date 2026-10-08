package com.project.handongjudge.hint.controller;

import com.project.handongjudge.hint.dto.HintRequestDTO;
import com.project.handongjudge.hint.dto.HintResponseDTO;
import com.project.handongjudge.hint.dto.HintUsageResponseDTO;
import com.project.handongjudge.hint.service.HintService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** "테스트하기"에서 학생이 선택한 테스트케이스에 대한 힌트. 과제 제출(채점용)에는 쓰지 않음. */
@RestController
@RequestMapping("/api/problems/{problemId}/hint")
@RequiredArgsConstructor
public class HintController {

    private final HintService hintService;

    /** 힌트 버튼을 처음 그릴 때 남은 횟수 표시용 (소모하지 않음). */
    @GetMapping("/usage")
    public ResponseEntity<HintUsageResponseDTO> getHintUsage(
            Authentication authentication,
            @PathVariable Long problemId) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(hintService.getRemainingUses(userId, problemId));
    }

    /** 학생이 특정 테스트케이스에서 "힌트" 버튼을 눌렀을 때 호출. 문제당 최대 3회(초과 시 HINT_LIMIT_EXCEEDED). */
    @PostMapping
    public ResponseEntity<HintResponseDTO> requestHint(
            Authentication authentication,
            @PathVariable Long problemId,
            @RequestBody HintRequestDTO request) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(hintService.requestHint(userId, problemId, request));
    }
}
