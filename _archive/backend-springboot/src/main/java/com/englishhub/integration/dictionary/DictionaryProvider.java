package com.englishhub.integration.dictionary;

import java.util.List;

/** Cổng tra từ điển bên ngoài (Anh - Anh). Kết quả được cache vào bảng Words bởi DictionaryService. */
public interface DictionaryProvider {

    /** Danh sách mục từ (mỗi từ loại một mục); rỗng nếu không tìm thấy hoặc dịch vụ lỗi. */
    List<DictionaryEntry> lookup(String word);

    record DictionaryEntry(
            String headword,
            String partOfSpeech,
            String phoneticUk,
            String phoneticUs,
            String audioUrlUk,
            String audioUrlUs,
            String definitionEn,
            String example) {
    }
}
