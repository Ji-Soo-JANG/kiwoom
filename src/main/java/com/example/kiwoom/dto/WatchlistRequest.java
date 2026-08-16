package com.example.kiwoom.dto;

import jakarta.validation.constraints.Pattern;

public record WatchlistRequest(
        @Pattern(regexp = "\\d{6}", message = "종목 코드는 6자리 숫자여야 합니다") String code,
        String groupName,
        String note) {}
