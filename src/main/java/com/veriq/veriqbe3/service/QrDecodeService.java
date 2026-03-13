package com.veriq.veriqbe3.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.veriq.veriqbe3.entity.ScanHistory;
import com.veriq.veriqbe3.repository.ScanHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class QrDecodeService {
    private final ScanHistoryRepository scanHistoryRepository;

    public String decodeQrImage(MultipartFile file, String guestId) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                throw new IllegalArgumentException("INVALID_IMAGE_FORMAT");
            }

            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

            Result result = new MultiFormatReader().decode(bitmap);
            String url = result.getText();

            ScanHistory history = new ScanHistory();
            history.setGuestId(guestId);
            history.setOriginalUrl(url);

            scanHistoryRepository.save(history);

            return url;
        }
    }
}

