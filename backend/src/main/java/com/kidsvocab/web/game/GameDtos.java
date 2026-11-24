package com.kidsvocab.web.game;

import com.kidsvocab.app.game.GameService;
import com.kidsvocab.app.game.GameTaskService;
import com.kidsvocab.app.game.GameTaskService.TaskDetails;
import com.kidsvocab.domain.game.GameMode;
import com.kidsvocab.domain.game.GameTask;
import com.kidsvocab.domain.game.GameTaskWord;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class GameDtos {

    private GameDtos() {
    }

    // --- parent ----------------------------------------------------------------------------------

    public record CreateTaskRequest(@NotNull GameMode mode) {
    }

    public record TaskWordRequest(@NotBlank @Size(max = 60) String source, @NotBlank @Size(max = 60) String target) {
    }

    /** Topic as in the week builder; {@code count} defaults to as many as fit. */
    public record FillRequest(@Size(max = 40) @Pattern(regexp = "[\\p{L} '-]*") String topic,
                              @Min(1) @Max(12) Integer count) {
    }

    public record TaskWordView(long id, long conceptId, String prompt, String answer) {

        static TaskWordView of(GameTaskWord w) {
            return new TaskWordView(w.getId(), w.getConceptId(), w.getPromptText(), w.getAnswerText());
        }
    }

    public record TaskSummary(long id, GameMode mode, GameTask.Status status, int wordCount, int timesPlayed,
                              Instant createdAt, Instant givenAt) {

        static TaskSummary of(TaskDetails d) {
            GameTask t = d.task();
            return new TaskSummary(t.getId(), t.getMode(), t.getStatus(), d.words().size(), t.getTimesPlayed(),
                    t.getCreatedAt(), t.getGivenAt());
        }
    }

    public record TaskResponse(long id, long childId, GameMode mode, GameTask.Status status, int timesPlayed,
                               int minWords, int maxWords, List<TaskWordView> words) {

        static TaskResponse of(TaskDetails d) {
            GameTask t = d.task();
            return new TaskResponse(t.getId(), t.getChildId(), t.getMode(), t.getStatus(), t.getTimesPlayed(),
                    GameTaskService.MIN_WORDS, GameTaskService.maxWords(t.getMode()),
                    d.words().stream().map(TaskWordView::of).toList());
        }
    }

    // --- child -----------------------------------------------------------------------------------

    public record MenuWeek(long id, int wordCount) {
    }

    public record MenuTask(long id, GameMode mode, int wordCount) {
    }

    public record MenuResponse(MenuWeek week, List<MenuTask> tasks) {

        static MenuResponse of(GameService.Menu m) {
            return new MenuResponse(m.week() == null ? null : new MenuWeek(m.week().getId(), m.weekWords()),
                    m.tasks().stream().map(d -> new MenuTask(d.task().getId(), d.task().getMode(), d.words().size()))
                            .toList());
        }
    }

    /** {@code options} only for CHOICE, {@code grid} only for WORD_SEARCH. */
    public record GameResponse(GameMode mode, Long taskId, List<GameService.GameWord> words, GridView grid) {

        static GameResponse of(GameService.Game g) {
            return new GameResponse(g.mode(), g.taskId(), g.words(),
                    g.grid() == null ? null : new GridView(g.grid().size(), g.grid().rows()));
        }
    }

    public record GridView(int size, List<String> rows) {
    }
}
