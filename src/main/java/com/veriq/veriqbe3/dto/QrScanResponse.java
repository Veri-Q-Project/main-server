package com.veriq.veriqbe3.dto;

import com.veriq.veriqbe3.domain.SchemeType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class QrScanResponse {
    private String guestUuid;
    private SchemeType schemeType;
    private String typeInfo;
    private String status;
}