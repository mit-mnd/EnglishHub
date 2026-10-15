package com.englishhub.modules.game.controller;

import com.englishhub.modules.game.entity.GameResult.GameType;
import com.englishhub.modules.game.service.GameService;
import com.englishhub.modules.game.service.GameService.GameStats;
import com.englishhub.modules.game.service.GameService.GameWord;
import com.englishhub.modules.game.service.GameService.ResultRequest;
import com.englishhub.modules.game.service.GameService.ResultResponse;
import com.englishhub.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Trò chơi từ vựng")
@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;

    @Operation(summary = "Bộ từ để chơi (ưu tiên sổ từ của học viên, bổ sung từ kho chung); count 4-20")
    @GetMapping("/words")
    public List<GameWord> words(@AuthenticationPrincipal UserPrincipal principal,
                                @RequestParam GameType gameType,
                                @RequestParam(defaultValue = "8") int count) {
        return gameService.words(principal.id(), gameType, count);
    }

    @Operation(summary = "Lưu kết quả một lượt chơi; trả về kỷ lục cá nhân")
    @PostMapping("/results")
    public ResultResponse submit(@AuthenticationPrincipal UserPrincipal principal,
                                 @Valid @RequestBody ResultRequest request) {
        return gameService.submit(principal.id(), request);
    }

    @Operation(summary = "Thống kê theo loại trò chơi: số lượt, điểm cao nhất, lần chơi gần nhất")
    @GetMapping("/stats")
    public List<GameStats> stats(@AuthenticationPrincipal UserPrincipal principal) {
        return gameService.stats(principal.id());
    }
}
