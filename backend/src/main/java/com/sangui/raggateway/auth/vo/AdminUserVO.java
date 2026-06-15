package com.sangui.raggateway.auth.vo;

public class AdminUserVO {

    private Long id;
    private String username;
    private String status;

    public AdminUserVO(Long id, String username, String status) {
        this.id = id;
        this.username = username;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getStatus() {
        return status;
    }
}
