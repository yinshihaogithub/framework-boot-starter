package com.framework.core.result;

import lombok.Data;
import java.io.Serializable;
import java.util.List;

/**
 * 分页响应体
 */
@Data
public class PageResult<T> implements Serializable {

    private List<T> records;
    private long total;
    private int pageNum;
    private int pageSize;
    private int pages;

    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records);
        r.setTotal(total);
        r.setPageNum(pageNum);
        r.setPageSize(pageSize);
        r.setPages(pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0);
        return r;
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return of(List.of(), 0, pageNum, pageSize);
    }
}
