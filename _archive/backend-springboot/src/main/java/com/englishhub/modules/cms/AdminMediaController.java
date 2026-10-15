package com.englishhub.modules.cms;

import com.englishhub.common.exception.BusinessException;
import com.englishhub.common.exception.ErrorCode;
import com.englishhub.integration.storage.FileStorageService;
import com.englishhub.modules.cms.MediaSniffer.Detected;
import com.englishhub.modules.cms.MediaSniffer.Kind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Tải lên audio / video / ảnh cho nội dung học. File lưu qua {@link FileStorageService} dưới thư mục
 * "public-media" và được phục vụ công khai tại /media/uploads/** (xem {@link MediaWebConfig}).
 */
@Tag(name = "Admin - media")
@RestController
@RequestMapping("/api/v1/admin/media")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminMediaController {

    static final String FOLDER = "public-media";
    static final String PUBLIC_PREFIX = "/media/uploads/";
    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    public record UploadResponse(String url, Kind kind, String extension, long sizeBytes) {
    }

    private final FileStorageService storage;

    @Operation(summary = "Tải lên media (kind = AUDIO | VIDEO | IMAGE). Định dạng nhận diện theo nội dung file")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestPart("file") MultipartFile file, @RequestParam Kind kind) throws IOException {
        byte[] bytes = file.getBytes();
        Detected detected = MediaSniffer.detect(bytes).orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
                "Định dạng không hỗ trợ. Audio: mp3, wav, m4a, ogg · Video: mp4, webm · Ảnh: jpg, png, webp, gif"));
        if (detected.kind() != kind) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "File là " + detected.extension() + " (" + detected.kind() + "), không phải " + kind);
        }
        if (kind == Kind.IMAGE && bytes.length > MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ảnh tối đa 5 MB");
        }
        String key = storage.store(FOLDER, detected.extension(), bytes);
        String fileName = key.substring(key.lastIndexOf('/') + 1);
        return new UploadResponse(PUBLIC_PREFIX + fileName, kind, detected.extension(), bytes.length);
    }
}
