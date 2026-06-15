package com.sangui.raggateway.log;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("rag_request_log_output_access_audit")
public class RequestLogOutputAccessAuditEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long appId;
    private Long requestLogId;
    private String requestId;
    private String accessResult;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public Long getRequestLogId() { return requestLogId; }
    public void setRequestLogId(Long requestLogId) { this.requestLogId = requestLogId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getAccessResult() { return accessResult; }
    public void setAccessResult(String accessResult) { this.accessResult = accessResult; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
