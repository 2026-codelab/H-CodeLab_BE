package com.project.handongjudge.systemadmin.controller;

import com.project.handongjudge.systemadmin.service.SystemAdminService;
import com.project.handongjudge.user.entity.User;
import com.project.handongjudge.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system-admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = {"${FRONTEND_URL:http://localhost:3000}"})
public class SystemAdminController {

    private final SystemAdminService systemAdminService;
    private final UserRepository userRepository;

    /**
     * 시스템 관리자 대시보드 통계 조회
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSuperAdminStats(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                log.error("인증 정보가 없습니다.");
                return buildErrorResponse("인증이 필요합니다.");
            }

            Long userId = Long.parseLong(authentication.getName());
            log.debug("시스템 관리자 통계 조회 요청 - userId: {}", userId);

            User user = userRepository.findById(userId)
                    .orElseThrow(() -> {
                        log.error("사용자를 찾을 수 없습니다: {}", userId);
                        return new RuntimeException("User not found");
                    });

            // SUPER_ADMIN 권한 확인
            if (user.getRole() != User.Role.SUPER_ADMIN) {
                log.warn("시스템 관리자 권한이 없는 사용자의 통계 조회 시도 - userId: {}, role: {}", userId, user.getRole());
                return buildErrorResponse("시스템 관리자 권한이 필요합니다.");
            }

            log.debug("시스템 관리자 통계 조회 시작 - userId: {}", userId);
            Map<String, Long> stats = systemAdminService.getSuperAdminStats();
            log.debug("시스템 관리자 통계 조회 완료 - stats: {}", stats);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "통계 조회 성공");
            response.put("data", stats);
            
            log.info("시스템 관리자 통계 응답 생성 완료");
            return ResponseEntity.ok(response);

        } catch (NumberFormatException e) {
            log.error("사용자 ID 파싱 실패: {}", authentication != null ? authentication.getName() : "null", e);
            return buildErrorResponse("잘못된 사용자 정보입니다.");
        } catch (Exception e) {
            log.error("시스템 관리자 통계 조회 실패: {}", 
                    authentication != null ? authentication.getName() : "null", e);
            return buildErrorResponse("통계를 가져오지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 시스템 관리자용 모든 수업 조회
     */
    @GetMapping("/sections")
    public ResponseEntity<Map<String, Object>> getAllSections(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                log.error("인증 정보가 없습니다.");
                return buildErrorResponse("인증이 필요합니다.");
            }

            Long userId = Long.parseLong(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // SUPER_ADMIN 권한 확인
            if (user.getRole() != User.Role.SUPER_ADMIN) {
                log.warn("시스템 관리자 권한이 없는 사용자의 수업 조회 시도 - userId: {}, role: {}", userId, user.getRole());
                return buildErrorResponse("시스템 관리자 권한이 필요합니다.");
            }

            List<com.project.handongjudge.user.dto.DashboardCourseDto> sections = systemAdminService.getAllSections();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "수업 목록 조회 성공");
            response.put("data", sections);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("수업 목록 조회 실패: {}", authentication != null ? authentication.getName() : "null", e);
            return buildErrorResponse("수업 목록을 가져오지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 시스템 관리자용 모든 사용자 조회
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers(Authentication authentication) {
        try {
            if (authentication == null || authentication.getName() == null) {
                log.error("인증 정보가 없습니다.");
                return buildErrorResponse("인증이 필요합니다.");
            }

            Long userId = Long.parseLong(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // SUPER_ADMIN 권한 확인
            if (user.getRole() != User.Role.SUPER_ADMIN) {
                log.warn("시스템 관리자 권한이 없는 사용자의 사용자 조회 시도 - userId: {}, role: {}", userId, user.getRole());
                return buildErrorResponse("시스템 관리자 권한이 필요합니다.");
            }

            List<com.project.handongjudge.user.dto.UserDto> users = systemAdminService.getAllUsers();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "사용자 목록 조회 성공");
            response.put("data", users);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("사용자 목록 조회 실패: {}", authentication != null ? authentication.getName() : "null", e);
            return buildErrorResponse("사용자 목록을 가져오지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 시스템 관리자용 사용자 역할 수정
     */
    @PatchMapping("/users/{userId}/role")
    public ResponseEntity<Map<String, Object>> updateUserRole(
            Authentication authentication,
            @PathVariable Long userId,
            @RequestBody com.project.handongjudge.systemadmin.dto.UpdateUserRoleRequest request) {
        try {
            if (authentication == null || authentication.getName() == null) {
                log.error("인증 정보가 없습니다.");
                return buildErrorResponse("인증이 필요합니다.");
            }

            Long adminId = Long.parseLong(authentication.getName());
            User admin = userRepository.findById(adminId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // SUPER_ADMIN 권한 확인
            if (admin.getRole() != User.Role.SUPER_ADMIN) {
                log.warn("시스템 관리자 권한이 없는 사용자의 역할 변경 시도 - userId: {}, role: {}", adminId, admin.getRole());
                return buildErrorResponse("시스템 관리자 권한이 필요합니다.");
            }

            // 본인 역할은 본인이 바꾸지 못하도록 막음 (실수로 권한을 잃어버리는 것 방지)
            if (adminId.equals(userId)) {
                return buildErrorResponse("본인의 역할은 변경할 수 없습니다.");
            }

            User.Role newRole;
            try {
                newRole = User.Role.valueOf(request.getRole());
            } catch (IllegalArgumentException | NullPointerException e) {
                return buildErrorResponse("유효하지 않은 역할입니다: " + request.getRole());
            }

            // 전역 ADMIN은 담당하지 않는 수업의 학생 데이터까지 열람 가능하게 하므로 당분간 할당을 막음
            if (newRole == User.Role.ADMIN) {
                return buildErrorResponse("관리자 역할은 현재 할당할 수 없습니다.");
            }

            com.project.handongjudge.user.dto.UserDto updated =
                    systemAdminService.updateUserRole(userId, newRole);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "사용자 역할 변경 성공");
            response.put("data", updated);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            return buildErrorResponse(e.getMessage());
        } catch (Exception e) {
            log.error("사용자 역할 변경 실패: {}", authentication != null ? authentication.getName() : "null", e);
            return buildErrorResponse("사용자 역할을 변경하지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 시스템 관리자용 모든 제출 레코드 조회
     */
    @GetMapping("/submissions")
    public ResponseEntity<Map<String, Object>> getAllSubmissions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        try {
            if (authentication == null || authentication.getName() == null) {
                log.error("인증 정보가 없습니다.");
                return buildErrorResponse("인증이 필요합니다.");
            }

            Long userId = Long.parseLong(authentication.getName());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            // SUPER_ADMIN 권한 확인
            if (user.getRole() != User.Role.SUPER_ADMIN) {
                log.warn("시스템 관리자 권한이 없는 사용자의 제출 조회 시도 - userId: {}, role: {}", userId, user.getRole());
                return buildErrorResponse("시스템 관리자 권한이 필요합니다.");
            }

            List<com.project.handongjudge.systemadmin.dto.SubmissionDetailDto> submissions = 
                    systemAdminService.getAllSubmissions(page, size);
            long totalCount = systemAdminService.getTotalSubmissionsCount();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "제출 목록 조회 성공");
            response.put("data", submissions);
            response.put("totalCount", totalCount);
            response.put("page", page);
            response.put("size", size);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("제출 목록 조회 실패: {}", authentication != null ? authentication.getName() : "null", e);
            return buildErrorResponse("제출 목록을 가져오지 못했습니다: " + e.getMessage());
        }
    }

    /**
     * 공통 에러 응답 생성
     */
    private ResponseEntity<Map<String, Object>> buildErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return ResponseEntity.badRequest().body(error);
    }
}

