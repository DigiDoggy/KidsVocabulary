package com.kidsvocab.app.game;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.app.week.WeekLifecycleService;
import com.kidsvocab.core.game.Choices;
import com.kidsvocab.core.week.WeekCalendar;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.child.Level;
import com.kidsvocab.domain.dictionary.DictionaryQueries;
import com.kidsvocab.domain.game.GameMode;
import com.kidsvocab.domain.game.GameTask;
import com.kidsvocab.domain.game.GameTaskRepository;
import com.kidsvocab.domain.game.GameTaskWord;
import com.kidsvocab.domain.game.GameTaskWordRepository;
import com.kidsvocab.domain.game.WeekWordSearch;
import com.kidsvocab.domain.game.WeekWordSearchRepository;
import com.kidsvocab.domain.game.WordSearchGrid;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.StudyWeekRepository;
import com.kidsvocab.domain.study.WeekStatus;
import com.kidsvocab.domain.study.WeekWord;
import com.kidsvocab.domain.study.WeekWordRepository;
import com.kidsvocab.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The child's games: this week's words in any game mode, and the tasks given by the parent. Games are never
 * scored; nothing here touches points, outcomes or the word history.
 */
@Service
public class GameService {

    /** How many of a week's words go into its word search. */
    static final int WEEK_GRID_WORDS = 8;
    private static final int POOL = 30;
    private static final List<WeekStatus> RUNNING_OR_CLOSED =
            List.of(WeekStatus.ACTIVE, WeekStatus.COMPLETED, WeekStatus.EXPIRED);

    private final ChildRepository children;
    private final StudyWeekRepository weeks;
    private final WeekWordRepository weekWords;
    private final GameTaskRepository tasks;
    private final GameTaskWordRepository taskWords;
    private final WeekWordSearchRepository weekGrids;
    private final DictionaryQueries dictionary;
    private final WordSearchMaker wordSearch;
    private final WeekLifecycleService lifecycle;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SecureRandom seeds = new SecureRandom();

    public GameService(ChildRepository children, StudyWeekRepository weeks, WeekWordRepository weekWords,
                       GameTaskRepository tasks, GameTaskWordRepository taskWords, WeekWordSearchRepository weekGrids,
                       DictionaryQueries dictionary, WordSearchMaker wordSearch, WeekLifecycleService lifecycle,
                       JdbcTemplate jdbc, ObjectMapper json) {
        this.children = children;
        this.weeks = weeks;
        this.weekWords = weekWords;
        this.tasks = tasks;
        this.taskWords = taskWords;
        this.weekGrids = weekGrids;
        this.dictionary = dictionary;
        this.wordSearch = wordSearch;
        this.lifecycle = lifecycle;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** What the child can play: this week's words (if there is a set this week) and the given tasks. */
    public record Menu(StudyWeek week, int weekWords, List<GameTaskService.TaskDetails> tasks) {
    }

    /**
     * @param options the choices (CHOICE only), the answer among them
     */
    public record GameWord(long id, String prompt, String answer, List<String> options) {
    }

    /**
     * @param taskId null for a game with this week's words
     * @param words  the words to play; for a word search only those hidden in the grid
     * @param grid   WORD_SEARCH only
     */
    public record Game(GameMode mode, Long taskId, List<GameWord> words, WordSearchGrid grid) {
    }

    @Transactional
    public Menu menu(CurrentUser user) {
        Child child = child(user);
        Optional<StudyWeek> week = thisWeek(child);
        int count = week.map(w -> (int) weekWords.countByWeekId(w.getId())).orElse(0);
        List<GameTaskService.TaskDetails> given = tasks.findByChildIdAndStatusOrderByIdDesc(child.getId(),
                        GameTask.Status.GIVEN).stream()
                .map(t -> new GameTaskService.TaskDetails(t, taskWords.findByTaskIdOrderById(t.getId())))
                .toList();
        return new Menu(week.orElse(null), count, given);
    }

    @Transactional
    public Game weekGame(CurrentUser user, GameMode mode) {
        Child child = child(user);
        StudyWeek week = thisWeek(child).orElseThrow(() -> new AppException(Code.CONFLICT, "There is no set this week"));
        List<WeekWord> list = weekWords.findByWeekIdOrderById(week.getId());
        List<GameWord> words = list.stream()
                .map(w -> new GameWord(w.getId(), w.getPromptText(), w.getPrimaryAnswer(), null))
                .toList();
        Map<Long, Long> concepts = list.stream().collect(Collectors.toMap(WeekWord::getId, WeekWord::getConceptId));
        return switch (mode) {
            case CHOICE -> new Game(mode, null, withOptions(child, words, concepts), null);
            case SCRAMBLE -> new Game(mode, null, words, null);
            case WORD_SEARCH -> {
                WordSearchGrid grid = weekGrid(child, week, words);
                yield new Game(mode, null, hidden(words, grid), grid);
            }
        };
    }

    @Transactional(readOnly = true)
    public Game taskGame(CurrentUser user, long taskId) {
        Child child = child(user);
        GameTask task = givenTask(child, taskId);
        List<GameTaskWord> list = taskWords.findByTaskIdOrderById(task.getId());
        List<GameWord> words = list.stream()
                .map(w -> new GameWord(w.getId(), w.getPromptText(), w.getAnswerText(), null))
                .toList();
        return switch (task.getMode()) {
            case CHOICE -> new Game(task.getMode(), task.getId(), withOptions(child, words,
                    list.stream().collect(Collectors.toMap(GameTaskWord::getId, GameTaskWord::getConceptId))), null);
            case SCRAMBLE -> new Game(task.getMode(), task.getId(), words, null);
            case WORD_SEARCH -> new Game(task.getMode(), task.getId(), hidden(words, task.getGrid()), task.getGrid());
        };
    }

    /** The child finished a task once more (shown to the parent). */
    @Transactional
    public void played(CurrentUser user, long taskId) {
        givenTask(child(user), taskId).played();
    }

    // --- internals -------------------------------------------------------------------------------

    /**
     * Distractors come from the same list first, then from words the child has learned, then from the
     * word's dictionary topic, then from anywhere in the dictionary.
     */
    private List<GameWord> withOptions(Child child, List<GameWord> words, Map<Long, Long> conceptOf) {
        SplittableRandom random = new SplittableRandom(seeds.nextLong());
        List<String> sameList = words.stream().map(GameWord::answer).toList();
        List<String> learned = dictionary.learnedAnswers(child.getId(), child.getTargetLang(), POOL);
        Map<Long, List<String>> sameTopic = dictionary.sameTopicAnswers(conceptOf.values(), child.getTargetLang());
        List<String> anywhere = dictionary.randomAnswers(child.getTargetLang(), POOL);
        List<GameWord> result = new ArrayList<>();
        for (GameWord w : words) {
            List<String> topic = sameTopic.getOrDefault(conceptOf.get(w.id()), List.of());
            result.add(new GameWord(w.id(), w.prompt(), w.answer(),
                    Choices.options(w.answer(), List.of(sameList, learned, topic, anywhere), random)));
        }
        return result;
    }

    /** The week's grid, built once from up to {@value #WEEK_GRID_WORDS} random words of the week. */
    private WordSearchGrid weekGrid(Child child, StudyWeek week, List<GameWord> words) {
        Optional<WeekWordSearch> stored = weekGrids.findById(week.getId());
        if (stored.isPresent()) {
            return stored.get().getGrid();
        }
        List<GameWord> pick = new ArrayList<>(words);
        Collections.shuffle(pick, new Random(seeds.nextLong()));
        List<WordSearchMaker.Word> chosen = pick.stream().limit(WEEK_GRID_WORDS)
                .map(w -> new WordSearchMaker.Word(w.id(), w.answer())).toList();
        WordSearchGrid grid = wordSearch.make(chosen, child.getTargetLang(), child.getLevel() == Level.A2);
        // two quick requests may both build a grid: the first one stored wins
        try {
            jdbc.update("insert into week_word_search (week_id, grid) values (?, cast(? as jsonb)) "
                    + "on conflict (week_id) do nothing", week.getId(), json.writeValueAsString(grid));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        String saved = jdbc.queryForObject("select grid::text from week_word_search where week_id = ?",
                String.class, week.getId());
        try {
            return json.readValue(saved, WordSearchGrid.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The words hidden in the grid, in the grid's order. */
    private static List<GameWord> hidden(List<GameWord> words, WordSearchGrid grid) {
        Map<Long, GameWord> byId = words.stream().collect(Collectors.toMap(GameWord::id, Function.identity()));
        return grid.wordIds().stream().map(byId::get).toList();
    }

    private Optional<StudyWeek> thisWeek(Child child) {
        lifecycle.refresh(child);
        var monday = WeekCalendar.weekStart(lifecycle.today(child));
        return weeks.findByChildIdAndStatusIn(child.getId(), RUNNING_OR_CLOSED).stream()
                .filter(w -> w.getStartDate().equals(monday))
                .findFirst();
    }

    private GameTask givenTask(Child child, long taskId) {
        return tasks.findByIdAndChildId(taskId, child.getId())
                .filter(t -> t.getStatus() == GameTask.Status.GIVEN)
                .orElseThrow(() -> AppException.notFound("Task"));
    }

    private Child child(CurrentUser user) {
        if (user.isParent()) {
            throw new AppException(Code.FORBIDDEN, "Child mode required");
        }
        return children.findById(user.childId()).filter(c -> !c.isArchived())
                .orElseThrow(() -> AppException.notFound("Child"));
    }
}
