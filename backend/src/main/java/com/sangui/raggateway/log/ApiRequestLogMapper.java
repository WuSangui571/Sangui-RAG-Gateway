package com.sangui.raggateway.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Insert;

@Mapper
public interface ApiRequestLogMapper extends BaseMapper<ApiRequestLogEntity> {

    @Insert("""
            INSERT INTO rag_request_log (
                request_id,
                user_id,
                app_id,
                api_key_id,
                model,
                provider_name,
                status,
                error_code,
                latency_ms,
                upstream_latency_ms,
                prompt_tokens,
                completion_tokens,
                total_tokens,
                messages_count,
                question_summary,
                hit_chunk_ids,
                created_at,
                updated_at
            ) VALUES (
                #{requestId},
                #{userId},
                #{appId},
                #{apiKeyId},
                #{model},
                #{providerName},
                #{status},
                #{errorCode},
                #{latencyMs},
                #{upstreamLatencyMs},
                #{promptTokens},
                #{completionTokens},
                #{totalTokens},
                #{messagesCount},
                #{questionSummary},
                CASE WHEN #{hitChunkIds} IS NULL THEN NULL ELSE #{hitChunkIds}::jsonb END,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRequestLog(ApiRequestLogEntity entity);
}
