package com.kidsvocab.web.game;

import com.kidsvocab.app.game.GameService;
import com.kidsvocab.domain.game.GameMode;
import com.kidsvocab.security.CurrentUser;
import com.kidsvocab.web.game.GameDtos.GameResponse;
import com.kidsvocab.web.game.GameDtos.MenuResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The child's games (unscored). */
@RestController
@RequestMapping("/api/v1/me/games")
@PreAuthorize("hasAuthority('MODE_CHILD')")
public class GameController {

    private final GameService games;

    public GameController(GameService games) {
        this.games = games;
    }

    @GetMapping
    public MenuResponse menu(CurrentUser user) {
        return MenuResponse.of(games.menu(user));
    }

    @GetMapping("/week/{mode}")
    public GameResponse weekGame(CurrentUser user, @PathVariable GameMode mode) {
        return GameResponse.of(games.weekGame(user, mode));
    }

    @GetMapping("/tasks/{taskId}")
    public GameResponse taskGame(CurrentUser user, @PathVariable long taskId) {
        return GameResponse.of(games.taskGame(user, taskId));
    }

    @PostMapping("/tasks/{taskId}/played")
    public ResponseEntity<Void> played(CurrentUser user, @PathVariable long taskId) {
        games.played(user, taskId);
        return ResponseEntity.noContent().build();
    }
}
