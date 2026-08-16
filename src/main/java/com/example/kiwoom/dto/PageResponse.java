package com.example.kiwoom.dto;

import java.util.List;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
