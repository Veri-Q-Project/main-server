package com.veriq.veriqbe3.controller;

import com.veriq.veriqbe3.dto.QrScanResponse;
import com.veriq.veriqbe3.service.ProcessQrScan;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController //json반환 컨트롤러
@RequestMapping("/api/v1/scan")     //공통 경로
@RequiredArgsConstructor    //api 객체 생성
public class QrScanController {

    private final ProcessQrScan processQrScan;  //의존성 주입

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) //multipart_form_data 제한
    public ResponseEntity<QrScanResponse> uploadQrImage(    //반환 http body 타입 제한
            @RequestHeader(value = "guest_uuid", required = false ) String guestUuid,
            @RequestParam("image") MultipartFile image) {
        try {
            QrScanResponse response = processQrScan.toFront(image, guestUuid);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}