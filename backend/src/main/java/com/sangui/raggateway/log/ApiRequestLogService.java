package com.sangui.raggateway.log;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sangui.raggateway.document.DocumentChunkEntity;
import com.sangui.raggateway.document.DocumentChunkMapper;
import com.sangui.raggateway.document.DocumentEntity;
import com.sangui.raggateway.document.DocumentMapper;
import com.sangui.raggateway.log.vo.ApiRequestLogDetailVO;
import com.sangui.raggateway.log.vo.ApiRequestLogPageVO;
import com.sangui.raggateway.log.vo.ApiRequestLogVO;
import com.sangui.raggateway.log.vo.HitChunkSummaryVO;
import com.sangui.raggateway.log.vo.RequestLogOutputPreviewVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Profile("!test")
public class ApiRequestLogService {

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLogService.class);
    private static final int HIT_CHUNK_SUMMARY_MAX_CHARS = 200;

    private final ApiRequestLogMapper apiRequestLogMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentMapper documentMapper;
    private final RequestLogOutputAccessAuditMapper auditMapper;
    private final OutputCapturePolicy outputCapturePolicy;
    private final OutputCaptureProperties outputCaptureProperties;

    public ApiRequestLogService(ApiRequestLogMapper apiRequestLogMapper) {
        this(apiRequestLogMapper, null, null, null, null, null);
    }

    @Autowired
    public ApiRequestLogService(ApiRequestLogMapper apiRequestLogMapper,
                                 DocumentChunkMapper documentChunkMapper,
                                 DocumentMapper documentMapper,
                                 RequestLogOutputAccessAuditMapper auditMapper,
                                 OutputCapturePolicy outputCapturePolicy,
                                 OutputCaptureProperties outputCaptureProperties) {
        this.apiRequestLogMapper = apiRequestLogMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.documentMapper = documentMapper;
        this.auditMapper = auditMapper;
        this.outputCapturePolicy = outputCapturePolicy;
        this.outputCaptureProperties = outputCaptureProperties;
    }

    public void record(CreateRequestLogCommand command) {
        try {
            ApiRequestLogEntity entity = toEntity(command);
            apiRequestLogMapper.insertRequestLog(entity);
        } catch (Exception e) {
            log.error("Failed to persist request log for request_id={}, errorType={}",
                    command.getRequestId(), e.getClass().getSimpleName());
        }
    }

    public ApiRequestLogPageVO<ApiRequestLogVO> listRequestLogs(Long userId, Long appId, ApiRequestLogQuery query) {
        LambdaQueryWrapper<ApiRequestLogEntity> wrapper = buildFilterWrapper(userId, appId, query);

        long total = apiRequestLogMapper.selectCount(wrapper);

        LambdaQueryWrapper<ApiRequestLogEntity> listWrapper = buildFilterWrapper(userId, appId, query);
        listWrapper.orderByDesc(ApiRequestLogEntity::getCreatedAt);
        int page = query.getPage();
        int pageSize = query.getPageSize();
        listWrapper.last("LIMIT " + pageSize + " OFFSET " + (page - 1) * pageSize);

        List<ApiRequestLogEntity> entities = apiRequestLogMapper.selectList(listWrapper);
        List<ApiRequestLogVO> vos = entities.stream().map(ApiRequestLogVO::from).toList();

        return ApiRequestLogPageVO.of(vos, page, pageSize, total);
    }

    public ApiRequestLogEntity findByRequestIdAndUserAndApp(Long userId, Long appId, String requestId) {
        return apiRequestLogMapper.selectByRequestIdAndUserAndApp(userId, appId, requestId);
    }

    public ApiRequestLogDetailVO getRequestLogDetail(Long userId, Long appId, String requestId) {
        ApiRequestLogEntity entity = apiRequestLogMapper.selectByRequestIdAndUserAndApp(userId, appId, requestId);
        if (entity == null) {
            return null;
        }
        return ApiRequestLogDetailVO.from(entity);
    }

    public List<HitChunkSummaryVO> getHitChunkSummaries(Long userId, Long appId, Long knowledgeBaseId, String requestId) {
        ApiRequestLogEntity log = apiRequestLogMapper.selectByRequestIdAndUserAndApp(userId, appId, requestId);
        if (log == null) {
            return Collections.emptyList();
        }

        ApiRequestLogVO parsedVO = ApiRequestLogVO.from(log);
        List<Long> chunkIds = parsedVO.getHitChunkIds();
        if (chunkIds == null || chunkIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<DocumentChunkEntity> chunks = documentChunkMapper.selectByIdsAndUserAndKb(userId, knowledgeBaseId, chunkIds);
        if (chunks.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> documentIds = chunks.stream()
                .map(DocumentChunkEntity::getDocumentId)
                .distinct()
                .toList();
        List<DocumentEntity> documents = documentMapper.selectBatchIds(documentIds);
        Map<Long, DocumentEntity> documentMap = new LinkedHashMap<>();
        for (DocumentEntity doc : documents) {
            documentMap.put(doc.getId(), doc);
        }

        Map<Long, DocumentChunkEntity> chunkMap = new LinkedHashMap<>();
        for (DocumentChunkEntity chunk : chunks) {
            chunkMap.put(chunk.getId(), chunk);
        }

        List<HitChunkSummaryVO> summaries = new ArrayList<>();
        for (Long chunkId : chunkIds) {
            DocumentChunkEntity chunk = chunkMap.get(chunkId);
            if (chunk != null) {
                DocumentEntity doc = documentMap.get(chunk.getDocumentId());
                summaries.add(HitChunkSummaryVO.of(chunk, doc, HIT_CHUNK_SUMMARY_MAX_CHARS));
            }
        }
        return summaries;
    }

    private LambdaQueryWrapper<ApiRequestLogEntity> buildFilterWrapper(Long userId, Long appId, ApiRequestLogQuery query) {
        LambdaQueryWrapper<ApiRequestLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiRequestLogEntity::getUserId, userId);
        wrapper.eq(ApiRequestLogEntity::getAppId, appId);

        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq(ApiRequestLogEntity::getStatus, query.getStatus());
        }
        if (query.getErrorCode() != null && !query.getErrorCode().isBlank()) {
            wrapper.eq(ApiRequestLogEntity::getErrorCode, query.getErrorCode());
        }
        if (query.getStartTime() != null) {
            wrapper.ge(ApiRequestLogEntity::getCreatedAt, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(ApiRequestLogEntity::getCreatedAt, query.getEndTime());
        }
        return wrapper;
    }

    private ApiRequestLogEntity toEntity(CreateRequestLogCommand command) {
        ApiRequestLogEntity entity = new ApiRequestLogEntity();
        entity.setRequestId(command.getRequestId());
        entity.setUserId(command.getUserId());
        entity.setAppId(command.getAppId());
        entity.setApiKeyId(command.getApiKeyId());
        entity.setModel(command.getModel());
        entity.setProviderName(command.getProviderName());
        entity.setStatus(command.getStatus());
        entity.setErrorCode(command.getErrorCode());
        entity.setLatencyMs(command.getLatencyMs());
        entity.setUpstreamLatencyMs(command.getUpstreamLatencyMs());
        entity.setPromptTokens(command.getPromptTokens());
        entity.setCompletionTokens(command.getCompletionTokens());
        entity.setTotalTokens(command.getTotalTokens());
        entity.setMessagesCount(command.getMessagesCount());
        entity.setQuestionSummary(command.getQuestionSummary());
        entity.setHitChunkIds(command.getHitChunkIds());
        entity.setCompletionLength(command.getCompletionLength());
        entity.setOutputCaptureStatus(command.getOutputCaptureStatus());
        entity.setOutputPreview(command.getOutputPreview());
        entity.setOutputPreviewTruncated(command.getOutputPreviewTruncated());
        entity.setOutputRedacted(command.getOutputRedacted());
        entity.setOutputRetentionExpiresAt(command.getOutputRetentionExpiresAt());
        return entity;
    }

    public RequestLogOutputPreviewVO getOutputPreview(Long userId, Long appId, String requestId) {
        ApiRequestLogEntity entity = apiRequestLogMapper.selectByRequestIdAndUserAndApp(userId, appId, requestId);
        if (entity == null) {
            return null;
        }
        RequestLogOutputPreviewVO vo = new RequestLogOutputPreviewVO();
        vo.setRequestId(entity.getRequestId());
        vo.setOutputCaptureStatus(entity.getOutputCaptureStatus());
        vo.setCompletionLength(entity.getCompletionLength());
        vo.setOutputPreview(entity.getOutputPreview());
        vo.setOutputPreviewTruncated(entity.getOutputPreviewTruncated());
        vo.setOutputRedacted(entity.getOutputRedacted());
        vo.setOutputRetentionExpiresAt(entity.getOutputRetentionExpiresAt());
        return vo;
    }

    public void writeAccessAudit(Long userId, Long appId, Long requestLogId,
                                  String requestId, String accessResult, String reason) {
        try {
            RequestLogOutputAccessAuditEntity audit = new RequestLogOutputAccessAuditEntity();
            audit.setUserId(userId);
            audit.setAppId(appId);
            audit.setRequestLogId(requestLogId);
            audit.setRequestId(requestId);
            audit.setAccessResult(accessResult);
            audit.setReason(reason != null && reason.length() > outputCaptureProperties.getReasonMaxChars()
                    ? reason.substring(0, outputCaptureProperties.getReasonMaxChars()) : reason);
            audit.setCreatedAt(LocalDateTime.now());
            auditMapper.insertAudit(audit);
        } catch (Exception e) {
            log.error("Failed to write output access audit for request_id={}, errorType={}",
                    requestId, e.getClass().getSimpleName());
        }
    }

    public int cleanupExpiredOutputPreviews() {
        LocalDateTime now = LocalDateTime.now();
        List<ApiRequestLogEntity> expired = apiRequestLogMapper.selectExpiredOutputPreviews(now);
        int count = 0;
        for (ApiRequestLogEntity entity : expired) {
            try {
                apiRequestLogMapper.expireOutputPreview(entity.getId());
                count++;
            } catch (Exception e) {
                log.error("Failed to expire output preview for id={}, errorType={}",
                        entity.getId(), e.getClass().getSimpleName());
            }
        }
        return count;
    }
}
