package com.englishhub.integration.dictionary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Tắt tra từ điển ngoài (môi trường không có Internet): chỉ dùng kho từ nội bộ. */
@Component
@ConditionalOnProperty(name = "englishhub.dictionary.provider", havingValue = "none")
public class NoopDictionaryProvider implements DictionaryProvider {

    @Override
    public List<DictionaryEntry> lookup(String word) {
        return List.of();
    }
}
