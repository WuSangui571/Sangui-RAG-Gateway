package com.sangui.raggateway.log.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ApiRequestLogPageVO<T> {

    private List<T> items;
    private Integer page;
    @JsonProperty("page_size")
    private Integer pageSize;
    private Long total;

    public static <T> ApiRequestLogPageVO<T> of(List<T> items, Integer page, Integer pageSize, Long total) {
        ApiRequestLogPageVO<T> vo = new ApiRequestLogPageVO<>();
        vo.items = items;
        vo.page = page;
        vo.pageSize = pageSize;
        vo.total = total;
        return vo;
    }

    public List<T> getItems() { return items; }
    public Integer getPage() { return page; }
    public Integer getPageSize() { return pageSize; }
    public Long getTotal() { return total; }
}
