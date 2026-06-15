package com.sangui.raggateway.log;

import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.log.vo.ApiRequestLogDetailVO;
import com.sangui.raggateway.log.vo.ApiRequestLogPageVO;
import com.sangui.raggateway.log.vo.ApiRequestLogVO;
import com.sangui.raggateway.log.vo.HitChunkSummaryVO;
import com.sangui.raggateway.log.vo.RequestLogOutputPreviewVO;
import com.sangui.raggateway.log.dto.RequestLogOutputAccessDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/admin/apps/{appId}/request-logs")
@Profile("!test")
public class ApiRequestLogAdminController {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogAdminController.class);

    private final AppService appService;
    private final ApiRequestLogService apiRequestLogService;
    private final OutputCaptureProperties outputCaptureProperties;

    public ApiRequestLogAdminController(AppService appService,
                                        ApiRequestLogService apiRequestLogService,
                                        OutputCaptureProperties outputCaptureProperties) {
        this.appService = appService;
        this.apiRequestLogService = apiRequestLogService;
        this.outputCaptureProperties = outputCaptureProperties;
    }

    @GetMapping
    public ApiResponse<ApiRequestLogPageVO<ApiRequestLogVO>> listRequestLogs(
            @PathVariable Long appId,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(name = "error_code", required = false) String errorCode,
            @RequestParam(name = "start_time", required = false) String startTime,
            @RequestParam(name = "end_time", required = false) String endTime) {
        Long userId = getRequiredUserId();
        validateAppOwnership(userId, appId);
        validateListParams(page, pageSize, status);

        ApiRequestLogQuery query = new ApiRequestLogQuery();
        query.setPage(page);
        query.setPageSize(pageSize);
        query.setStatus(normalizeStatus(status));
        query.setErrorCode(errorCode != null && !errorCode.isBlank() ? errorCode : null);
        query.setStartTime(parseTime(startTime, "start_time"));
        query.setEndTime(parseTime(endTime, "end_time"));

        if (query.getStartTime() != null && query.getEndTime() != null && query.getStartTime().isAfter(query.getEndTime())) {
            throw new BusinessException("INVALID_REQUEST", "start_time must be before end_time");
        }

        ApiRequestLogPageVO<ApiRequestLogVO> pageVO = apiRequestLogService.listRequestLogs(userId, appId, query);
        return ApiResponse.success(pageVO);
    }

    @GetMapping("/{requestId}")
    public ApiResponse<ApiRequestLogDetailVO> getRequestLogDetail(
            @PathVariable Long appId,
            @PathVariable String requestId) {
        Long userId = getRequiredUserId();
        validateAppOwnership(userId, appId);

        ApiRequestLogDetailVO detail = apiRequestLogService.getRequestLogDetail(userId, appId, requestId);
        if (detail == null) {
            throw new BusinessException("NOT_FOUND", "Request log not found", HttpStatus.NOT_FOUND);
        }
        return ApiResponse.success(detail);
    }

    @GetMapping("/{requestId}/hit-chunks")
    public ApiResponse<List<HitChunkSummaryVO>> getHitChunks(
            @PathVariable Long appId,
            @PathVariable String requestId) {
        Long userId = getRequiredUserId();
        AppEntity app = validateAppOwnership(userId, appId);

        Long knowledgeBaseId = app.getDefaultKnowledgeBaseId();
        if (knowledgeBaseId == null) {
            throw new BusinessException("INVALID_REQUEST", "App has no default knowledge base");
        }
        if (apiRequestLogService.findByRequestIdAndUserAndApp(userId, appId, requestId) == null) {
            throw new BusinessException("NOT_FOUND", "Request log not found", HttpStatus.NOT_FOUND);
        }

        List<HitChunkSummaryVO> summaries = apiRequestLogService.getHitChunkSummaries(userId, appId, knowledgeBaseId, requestId);
        return ApiResponse.success(summaries);
    }

    @PostMapping("/{requestId}/output-preview/access")
    public ApiResponse<RequestLogOutputPreviewVO> accessOutputPreview(
            @PathVariable Long appId,
            @PathVariable String requestId,
            @RequestBody RequestLogOutputAccessDTO dto) {
        Long userId = getRequiredUserId();
        validateAppOwnership(userId, appId);

        ApiRequestLogEntity logEntity = apiRequestLogService.findByRequestIdAndUserAndApp(userId, appId, requestId);
        if (logEntity == null) {
            log.warn("Output preview access denied: request log not found for appId={} requestId={} userId={}",
                    appId, requestId, userId);
            apiRequestLogService.writeAccessAudit(userId, appId, null, requestId, "NOT_FOUND", null);
            throw new BusinessException("NOT_FOUND", "Request log not found", HttpStatus.NOT_FOUND);
        }

        if (dto == null || dto.getConfirmAccess() == null || !dto.getConfirmAccess()) {
            apiRequestLogService.writeAccessAudit(userId, appId, logEntity.getId(), requestId,
                    "DENIED", null);
            throw new BusinessException("INVALID_REQUEST",
                    "confirm_access must be true to view output preview");
        }

        String reason = dto.getReason() != null ? dto.getReason().trim() : null;
        if (reason != null && reason.length() > outputCaptureProperties.getReasonMaxChars()) {
            apiRequestLogService.writeAccessAudit(userId, appId, logEntity.getId(), requestId,
                    "DENIED", reason);
            throw new BusinessException("INVALID_REQUEST",
                    "reason must not exceed " + outputCaptureProperties.getReasonMaxChars() + " characters");
        }

        RequestLogOutputPreviewVO preview = apiRequestLogService.getOutputPreview(userId, appId, requestId);

        apiRequestLogService.writeAccessAudit(userId, appId, logEntity.getId(),
                requestId, "GRANTED", reason);

        return ApiResponse.success(preview);
    }

    private AppEntity validateAppOwnership(Long userId, Long appId) {
        AppEntity app = appService.findByIdAndUserId(appId, userId);
        if (app == null) {
            AppEntity anyApp = appService.findById(appId);
            if (anyApp != null) {
                throw new BusinessException("FORBIDDEN", "Access denied", HttpStatus.FORBIDDEN);
            }
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }
        return app;
    }

    private Long getRequiredUserId() {
        Long userId = AdminAuthContextHolder.getUserId();
        if (userId == null || userId <= 0) {
            throw new BusinessException("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    private void validateListParams(Integer page, Integer pageSize, String status) {
        if (page != null && page < 1) {
            throw new BusinessException("INVALID_REQUEST", "page must be positive");
        }
        if (pageSize != null && (pageSize < 1 || pageSize > 100)) {
            throw new BusinessException("INVALID_REQUEST", "page_size must be between 1 and 100");
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.toLowerCase();
            if (!"success".equals(normalized) && !"failure".equals(normalized)) {
                throw new BusinessException("INVALID_REQUEST", "status must be success or failure");
            }
        }
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.toLowerCase();
    }

    private LocalDateTime parseTime(String timeStr, String paramName) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(timeStr);
        } catch (DateTimeParseException e) {
            throw new BusinessException("INVALID_REQUEST", "Invalid " + paramName + " format, expected ISO format like 2026-05-31T00:00:00");
        }
    }
}
