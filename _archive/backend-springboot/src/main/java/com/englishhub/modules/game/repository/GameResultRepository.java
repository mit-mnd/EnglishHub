package com.englishhub.modules.game.repository;

import com.englishhub.modules.game.entity.GameResult;
import com.englishhub.modules.game.entity.GameResult.GameType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameResultRepository extends JpaRepository<GameResult, Long> {

    @Query("select max(g.score) from GameResult g where g.userId = :userId and g.gameType = :type")
    Optional<Integer> findBestScore(@Param("userId") Long userId, @Param("type") GameType type);

    /** [gameType, số lượt, điểm cao nhất, lần chơi gần nhất] */
    @Query("""
            select g.gameType, count(g), max(g.score), max(g.createdAt) from GameResult g
            where g.userId = :userId group by g.gameType""")
    List<Object[]> statsByType(@Param("userId") Long userId);
}
