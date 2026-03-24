package com.veriq.veriqbe3.service;

import com.veriq.veriqbe3.domain.SchemeType;
import com.veriq.veriqbe3.dto.QrScanResponse;
import com.veriq.veriqbe3.entity.ScanHistory;
import com.veriq.veriqbe3.repository.ScanHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProcessQrScan {

    private final QrDecoder qrDecoder;
    private final SchemeClassifier schemeClassifier;
    private final ScanHistoryRepository scanHistoryRepository;

    @Transactional // DB 저장 중 에러 발생 시 모든 작업을 롤백하기 위함
    public QrScanResponse toFront(MultipartFile image, String guest_uuid) throws Exception {

        String url = qrDecoder.decode(image);         //이미지 디코드

        SchemeClassifier.ClassificationResult result = schemeClassifier.classify(url);    //scheme 분류

        ScanHistory history = ScanHistory.builder()     //entity 생성
                .guestUuid(guest_uuid)
                .originalUrl(url)
                .extractedData(result.typeInfo())
                .schemeType(result.type())
                .build();
        scanHistoryRepository.save(history);            //repository에 저장

        String status = "COMPLETED";

        //WEB이나 단축 URL이면 상태를 변경
        if (result.type() == SchemeType.WEB || result.type() == SchemeType.SHORT_URL) {
            status = "ANALYSIS_REQUIRED";

        }
        return QrScanResponse.builder()
                .guestUuid(guest_uuid)          // FE가 보낸 식별자
                .schemeType(result.type())
                .typeInfo(result.typeInfo())
                .status(status)
                .build();
    }
}