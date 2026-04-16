package com.veriq.veriqbe3.dto;

import com.veriq.veriqbe3.domain.SchemeType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QrScanResponse {
    private String guestUuid;
    private SchemeType schemeType;
    private String typeInfo;
    private String status;
    @JsonProperty("isUrl")
    private boolean isUrl;
    private String message;
}