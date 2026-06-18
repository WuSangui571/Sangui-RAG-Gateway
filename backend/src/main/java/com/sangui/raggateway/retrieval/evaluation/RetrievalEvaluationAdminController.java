package com.sangui.raggateway.retrieval.evaluation;

import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.common.response.ApiResponse;
import com.sangui.raggateway.common.security.AdminAuthContextHolder;
import com.sangui.raggateway.retrieval.evaluation.dto.RetrievalEvaluationRunDTO;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/apps/{appId}/retrieval-evaluations")
@Profile("!test")
public class RetrievalEvaluationAdminController {

    private final AppService appService;
    private final RetrievalEvaluationService evaluationService;

    public RetrievalEvaluationAdminController(AppService appService,
                                               RetrievalEvaluationService evaluationService) {
        this.appService = appService;
        this.evaluationService = evaluationService;
    }

    @PostMapping("/runs")
    public ApiResponse<RetrievalEvaluationRunResult> runEvaluation(
            @PathVariable Long appId,
            @RequestBody(required = false) RetrievalEvaluationRunDTO dto) {
        Long userId = getRequiredUserId();
        validateAppOwnership(userId, appId);
        evaluationService.validateLimit(dto != null ? dto.getLimit() : null);

        List<String> caseIds = dto != null ? dto.getCaseIds() : null;
        Integer limit = dto != null ? dto.getLimit() : null;

        RetrievalEvaluationRunResult result = evaluationService.run(appId, userId, caseIds, limit);
        return ApiResponse.success(result);
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
}
