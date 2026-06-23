package com.sangui.raggateway.retrieval.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sangui.raggateway.app.AppEntity;
import com.sangui.raggateway.app.AppRetrievalConfig;
import com.sangui.raggateway.app.AppService;
import com.sangui.raggateway.common.exception.BusinessException;
import com.sangui.raggateway.knowledge.KnowledgeBaseEntity;
import com.sangui.raggateway.knowledge.KnowledgeBaseStatus;
import com.sangui.raggateway.retrieval.Citation;
import com.sangui.raggateway.retrieval.RetrievalResult;
import com.sangui.raggateway.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@Profile("!test")
public class RetrievalEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalEvaluationService.class);
    private static final String BASELINE_RESOURCE = "retrieval-evaluation/baseline-cases.jsonl";
    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final AppService appService;
    private final RetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    public RetrievalEvaluationService(AppService appService,
                                       RetrievalService retrievalService,
                                       ObjectMapper objectMapper) {
        this.appService = appService;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    public RetrievalEvaluationRunResult run(Long appId, Long userId,
                                             List<String> caseIds, Integer limit) {
        AppEntity app = appService.findByIdAndUserId(appId, userId);
        if (app == null) {
            throw new BusinessException("NOT_FOUND", "App not found", HttpStatus.NOT_FOUND);
        }

        KnowledgeBaseEntity kb = appService.resolveDefaultKnowledgeBase(app);
        if (kb == null || !KnowledgeBaseStatus.READY.name().equals(kb.getStatus())) {
            throw new BusinessException("KNOWLEDGE_BASE_NOT_READY",
                    "Knowledge base is not ready for this app", HttpStatus.BAD_REQUEST);
        }

        List<RetrievalEvaluationCase> allCases = loadCases();
        List<RetrievalEvaluationCase> selected = selectCases(allCases, caseIds, limit);
        if (selected.isEmpty()) {
            throw new BusinessException("INVALID_REQUEST",
                    "Evaluation sample set is empty", HttpStatus.BAD_REQUEST);
        }

        AppRetrievalConfig config;
        try {
            config = appService.resolveRetrievalConfig(app);
        } catch (IllegalArgumentException e) {
            log.error("Invalid retrieval config for appId={}, reason={}", app.getId(), e.getMessage());
            throw new BusinessException("INVALID_REQUEST",
                    "App retrieval config is invalid: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        }

        int topK = config.getTopK();
        double threshold = config.getSimilarityThreshold();
        int maxChunks = config.getMaxContextChunks();
        int maxChars = config.getMaxContextChars();
        int maxSingleChars = config.getMaxSingleChunkChars();

        List<RetrievalEvaluationCaseResult> caseResults = new ArrayList<>(selected.size());
        int hitCount = 0;
        double precisionSum = 0.0;
        double recallSum = 0.0;
        double mrrSum = 0.0;

        for (RetrievalEvaluationCase evalCase : selected) {
            RetrievalEvaluationCaseResult caseResult = evaluateCase(evalCase, kb,
                    topK, threshold, maxChunks, maxChars, maxSingleChars);
            caseResults.add(caseResult);
            if (caseResult.isHit()) {
                hitCount++;
            }
            precisionSum += caseResult.getPrecisionAtK();
            recallSum += caseResult.getRecallAtK();
            mrrSum += caseResult.getMrr();
        }

        int n = caseResults.size();
        return new RetrievalEvaluationRunResult(
                appId, kb.getId(), n, hitCount,
                round(precisionSum / n), round(recallSum / n), round(mrrSum / n),
                caseResults);
    }

    private RetrievalEvaluationCaseResult evaluateCase(RetrievalEvaluationCase evalCase,
                                                        KnowledgeBaseEntity kb,
                                                        int topK, double threshold,
                                                        int maxChunks, int maxChars, int maxSingleChars) {
        String query = evalCase.getQuery();
        try {
            RetrievalResult result = retrievalService.retrieve(
                    query, kb, topK, threshold, maxChunks, maxChars, maxSingleChars);

            List<Long> actualChunkIds = result.getHitChunkIds() != null ? result.getHitChunkIds() : List.of();
            List<Long> actualDocIds = result.getCitations() != null
                    ? result.getCitations().stream().map(Citation::getDocumentId).distinct().toList()
                    : List.of();

            List<Long> expectedChunkIds = evalCase.getExpectedChunkIds() != null
                    ? evalCase.getExpectedChunkIds() : List.of();
            List<Long> expectedDocIds = evalCase.getExpectedDocumentIds() != null
                    ? evalCase.getExpectedDocumentIds() : List.of();

            Set<Long> expectedChunkSet = new HashSet<>(expectedChunkIds);
            int relevantRetrieved = 0;
            Integer rank = null;
            for (int i = 0; i < actualChunkIds.size(); i++) {
                Long chunkId = actualChunkIds.get(i);
                if (expectedChunkSet.contains(chunkId)) {
                    relevantRetrieved++;
                    if (rank == null) {
                        rank = i + 1;
                    }
                }
            }

            boolean noHits = actualChunkIds.isEmpty();
            boolean baseHit;
            if (!expectedChunkIds.isEmpty()) {
                baseHit = relevantRetrieved > 0;
            } else if (!expectedDocIds.isEmpty()) {
                Set<Long> actualDocSet = new HashSet<>(actualDocIds);
                baseHit = expectedDocIds.stream().anyMatch(actualDocSet::contains);
            } else {
                baseHit = noHits;
            }

            boolean filenameOk = evalCase.getRequiredSourceFilename() == null
                    || (result.getCitations() != null && result.getCitations().stream()
                        .anyMatch(c -> evalCase.getRequiredSourceFilename().equals(c.getSourceFilename())));
            boolean similarityOk = evalCase.getMinExpectedSimilarity() == null
                    || (result.getCitations() != null && result.getCitations().stream()
                        .anyMatch(c -> c.getSimilarity() != null
                                && c.getSimilarity() >= evalCase.getMinExpectedSimilarity()));

            boolean hit = baseHit && filenameOk && similarityOk;

            double precision = actualChunkIds.isEmpty() ? 0.0 : (double) relevantRetrieved / actualChunkIds.size();
            double recall = expectedChunkIds.isEmpty() ? 1.0 : (double) relevantRetrieved / expectedChunkIds.size();
            double mrr = rank != null ? 1.0 / rank : 0.0;

            return new RetrievalEvaluationCaseResult(
                    evalCase.getCaseId(), query,
                    expectedChunkIds, actualChunkIds,
                    expectedDocIds, actualDocIds,
                    hit, rank, round(precision), round(recall), round(mrr),
                    noHits, null);
        } catch (Exception e) {
            log.warn("Retrieval evaluation case failed: case_id={}, errorType={}",
                    evalCase.getCaseId(), e.getClass().getSimpleName());
            return new RetrievalEvaluationCaseResult(
                    evalCase.getCaseId(), query,
                    evalCase.getExpectedChunkIds() != null ? evalCase.getExpectedChunkIds() : List.of(),
                    List.of(),
                    evalCase.getExpectedDocumentIds() != null ? evalCase.getExpectedDocumentIds() : List.of(),
                    List.of(),
                    false, null, 0.0, 0.0, 0.0, true, "retrieval_error");
        }
    }

    private List<RetrievalEvaluationCase> loadCases() {
        List<RetrievalEvaluationCase> cases = new ArrayList<>();
        try (InputStream in = new ClassPathResource(BASELINE_RESOURCE).getInputStream()) {
            String content = new String(in.readAllBytes());
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                cases.add(objectMapper.readValue(trimmed, RetrievalEvaluationCase.class));
            }
        } catch (Exception e) {
            log.error("Failed to load retrieval evaluation baseline cases, errorType={}",
                    e.getClass().getSimpleName());
            throw new BusinessException("INTERNAL_ERROR",
                    "Failed to load evaluation baseline cases", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return cases;
    }

    private List<RetrievalEvaluationCase> selectCases(List<RetrievalEvaluationCase> all,
                                                       List<String> caseIds, Integer limit) {
        List<RetrievalEvaluationCase> filtered = all;
        if (caseIds != null && !caseIds.isEmpty()) {
            Set<String> wanted = new HashSet<>(caseIds);
            filtered = all.stream().filter(c -> wanted.contains(c.getCaseId())).toList();
        }
        int effectiveLimit = limit != null ? Math.min(Math.max(limit, 1), MAX_LIMIT) : DEFAULT_LIMIT;
        int bounded = Math.min(effectiveLimit, filtered.size());
        return filtered.subList(0, bounded);
    }

    void validateLimit(Integer limit) {
        if (limit != null && (limit < 1 || limit > MAX_LIMIT)) {
            throw new BusinessException("INVALID_REQUEST",
                    "limit must be between 1 and " + MAX_LIMIT);
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
