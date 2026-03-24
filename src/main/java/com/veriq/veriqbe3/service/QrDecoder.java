package com.veriq.veriqbe3.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

@Component
public class QrDecoder {
    public String decode(MultipartFile file) throws Exception {
        try (InputStream inputStream = file.getInputStream()) {                 //raw data로 저장
            BufferedImage bufferedImage = ImageIO.read(inputStream);            //raw data를 이미지 객체로 변환
            if (bufferedImage == null) {                                        //이미지가 아닌 경우 예외처리
                throw new IllegalArgumentException("INVALID_IMAGE_FORMAT");
            }

            BufferedImageLuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);  //qr이미지의 색상 정보 제거
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));                    //흑백으로 이진화

            Result result = new MultiFormatReader().decode(bitmap); //이미지를 픽셀 데이터로 변환

            return result.getText();                                //Zxing라이브러리로 url 추출
        }
    }
}

