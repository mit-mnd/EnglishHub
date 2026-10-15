package com.englishhub.integration.storage;

/** Cổng lưu file người dùng tải lên. Bản local lưu ổ đĩa; có thể thay bằng S3 / Azure Blob. */
public interface FileStorageService {

    /**
     * @param folder    thư mục logic, vd. "speaking/12"
     * @param extension đuôi file không có dấu chấm, vd. "wav"
     * @return khóa lưu trữ (đường dẫn tương đối) để đọc lại sau
     */
    String store(String folder, String extension, byte[] content);

    byte[] read(String key);
}
