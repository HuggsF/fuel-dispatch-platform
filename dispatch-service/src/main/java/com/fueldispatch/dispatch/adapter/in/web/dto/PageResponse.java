package com.fueldispatch.dispatch.adapter.in.web.dto;

import java.util.List;

/** One page of a listing; {@code page} starts at 0. */
public record PageResponse<T>(
        List<T> items, int page, int size, long totalElements, long totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        long totalPages = (totalElements + size - 1) / size;
        return new PageResponse<>(List.copyOf(items), page, size, totalElements, totalPages);
    }
}
