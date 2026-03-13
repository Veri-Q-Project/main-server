package com.veriq.veriqbe3.controller;

import com.veriq.veriqbe3.service.QrDecodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/scan")
@RequiredArgsConstructor
public class QrScanController {

    private final QrDecodeService qrDecodeService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadQrImage(
            @RequestHeader(value = "X-GUEST-ID", required = false ) String guestId,
            @RequestParam("image") MultipartFile image) {
        try {
            String extractedUrl = qrDecodeService.decodeQrImage(image, guestId);
            return ResponseEntity.ok(extractedUrl);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("DECODE_FAILED");
        }
    }
}