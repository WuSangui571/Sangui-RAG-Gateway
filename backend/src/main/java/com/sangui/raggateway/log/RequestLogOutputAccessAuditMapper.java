package com.sangui.raggateway.log;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface RequestLogOutputAccessAuditMapper extends BaseMapper<RequestLogOutputAccessAuditEntity> {

    @Insert("""
            INSERT INTO rag_request_log_output_access_audit (
                user_id,
                app_id,
                request_log_id,
                request_id,
                access_result,
                reason,
                created_at
            ) VALUES (
                #{userId},
                #{appId},
                #{requestLogId},
                #{requestId},
                #{accessResult},
                #{reason},
                CURRENT_TIMESTAMP
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAudit(RequestLogOutputAccessAuditEntity entity);
}
