package com.japanese.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DisplayNameRequest(
        @NotBlank(message = "표시 이름을 입력하세요.")
        @Size(max = 80, message = "표시 이름은 80자 이하여야 합니다.") String displayName) {}
