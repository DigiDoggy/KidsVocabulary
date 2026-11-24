package com.kidsvocab.web.game;

import com.kidsvocab.app.game.GameTaskService;
import com.kidsvocab.security.CurrentUser;
import com.kidsvocab.web.game.GameDtos.CreateTaskRequest;
import com.kidsvocab.web.game.GameDtos.FillRequest;
import com.kidsvocab.web.game.GameDtos.TaskResponse;
import com.kidsvocab.web.game.GameDtos.TaskSummary;
import com.kidsvocab.web.game.GameDtos.TaskWordRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/** Game tasks prepared by the parent. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('MODE_PARENT')")
public class GameTaskController {

    private final GameTaskService tasks;

    public GameTaskController(GameTaskService tasks) {
        this.tasks = tasks;
    }

    @GetMapping("/children/{childId}/game-tasks")
    public List<TaskSummary> list(CurrentUser user, @PathVariable long childId) {
        return tasks.list(user, childId).stream().map(TaskSummary::of).toList();
    }

    @PostMapping("/children/{childId}/game-tasks")
    public ResponseEntity<TaskResponse> create(CurrentUser user, @PathVariable long childId,
                                               @Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(TaskResponse.of(tasks.create(user, childId, request.mode())));
    }

    @GetMapping("/game-tasks/{taskId}")
    public TaskResponse get(CurrentUser user, @PathVariable long taskId) {
        return TaskResponse.of(tasks.get(user, taskId));
    }

    @DeleteMapping("/game-tasks/{taskId}")
    public ResponseEntity<Void> delete(CurrentUser user, @PathVariable long taskId) {
        tasks.delete(user, taskId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/game-tasks/{taskId}/words")
    public TaskResponse addWord(CurrentUser user, @PathVariable long taskId,
                                @Valid @RequestBody TaskWordRequest request) {
        return TaskResponse.of(tasks.addWord(user, taskId, request.source(), request.target()));
    }

    @DeleteMapping("/game-tasks/{taskId}/words/{wordId}")
    public TaskResponse removeWord(CurrentUser user, @PathVariable long taskId, @PathVariable long wordId) {
        return TaskResponse.of(tasks.removeWord(user, taskId, wordId));
    }

    @PostMapping("/game-tasks/{taskId}/suggest")
    public TaskResponse fill(CurrentUser user, @PathVariable long taskId,
                             @Valid @RequestBody(required = false) FillRequest request) {
        String topic = request == null || request.topic() == null || request.topic().isBlank() ? null
                : request.topic().strip().replaceAll(" +", " ").toLowerCase(Locale.ROOT);
        return TaskResponse.of(tasks.fill(user, taskId, topic, request == null ? null : request.count()));
    }

    @PostMapping("/game-tasks/{taskId}/give")
    public TaskResponse give(CurrentUser user, @PathVariable long taskId) {
        return TaskResponse.of(tasks.give(user, taskId));
    }
}
