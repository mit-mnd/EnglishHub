package com.englishhub.modules.catalog.repository;

import com.englishhub.modules.catalog.entity.Level;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LevelRepository extends JpaRepository<Level, Integer> {

    List<Level> findAllByOrderBySortOrderAsc();

    Optional<Level> findByCode(String code);
}
