package com.example.kiwoom.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void calculatesTotalPages() {
        assertThat(new PageResponse<>(List.of("item"), 0, 10, 21).totalPages()).isEqualTo(3);
        assertThat(new PageResponse<>(List.of(), 0, 0, 0).totalPages()).isZero();
    }
}
