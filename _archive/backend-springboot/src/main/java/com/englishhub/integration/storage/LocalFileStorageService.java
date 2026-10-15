package com.englishhub.integration.storage;

import com.englishhub.common.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

/** Lưu file trên ổ đĩa server (thư mục cấu hình englishhub.storage.dir). Tên file sinh ngẫu nhiên. */
@Service
public class LocalFileStorageService implements FileStorageService {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[a-z0-9-]+(/[a-z0-9-]+)*");
    private static final Pattern SAFE_EXT = Pattern.compile("[a-z0-9]{1,5}");

    private final Path root;

    public LocalFileStorageService(AppProperties props) {
        this.root = Path.of(props.storage().dir()).toAbsolutePath().normalize();
    }

    @Override
    public String store(String folder, String extension, byte[] content) {
        if (!SAFE_SEGMENT.matcher(folder).matches() || !SAFE_EXT.matcher(extension).matches()) {
            throw new IllegalArgumentException("Tên thư mục/đuôi file không hợp lệ");
        }
        String key = folder + "/" + UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Không lưu được file", e);
        }
        return key;
    }

    @Override
    public byte[] read(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được file " + key, e);
        }
    }

    /** Chặn path traversal: đường dẫn cuối cùng bắt buộc nằm trong thư mục gốc. */
    private Path resolve(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Đường dẫn không hợp lệ");
        }
        return path;
    }
}
