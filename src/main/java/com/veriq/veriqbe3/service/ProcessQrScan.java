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

    @Transactional // DB 저장 중 에러 발생 시 롤백하기 위함
    public QrScanResponse process(MultipartFile image, String guest_uuid) throws Exception {

        String url = qrDecoder.decode(image);         //이미지 디코드

        SchemeClassifier.ClassificationResult result = schemeClassifier.classify(url);    //scheme 분류

        String status = "COMPLETED";

        if (result.type() == SchemeType.WEB || result.type() == SchemeType.SHORT_URL) {

            ScanHistory history = ScanHistory.builder()     //entity 생성
                    .guestUuid(guest_uuid)
                    .originalUrl(url)
                    .extractedData(result.typeInfo())
                    .schemeType(result.type())
                    .build();
            scanHistoryRepository.save(history);            //repository에 저장

            return QrScanResponse.builder()
                    .guestUuid(guest_uuid)
                    .schemeType(result.type())
                    .typeInfo(result.typeInfo())
                    .status("ANALYSIS_COMPLETED") // BE2 연동 후 실제 결과 상태로 변경
                    .build();
        }
        return QrScanResponse.builder()
                .guestUuid(guest_uuid)
                .schemeType(result.type())
                .typeInfo(result.typeInfo())
                .status(status)
                .build();
    }
}