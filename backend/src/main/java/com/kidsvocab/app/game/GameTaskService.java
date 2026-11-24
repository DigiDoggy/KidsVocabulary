package com.kidsvocab.app.game;

import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.app.week.ParentWords;
import com.kidsvocab.app.week.WeekLifecycleService;
import com.kidsvocab.app.week.WordSuggestionSource;
import com.kidsvocab.core.game.WordSearch;
import com.kidsvocab.core.text.AnswerNormalizer;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.child.Level;
import com.kidsvocab.domain.dictionary.DictionaryQueries;
import com.kidsvocab.domain.dictionary.DictionaryQueries.Card;
import com.kidsvocab.domain.game.GameMode;
import com.kidsvocab.domain.game.GameTask;
import com.kidsvocab.domain.game.GameTaskRepository;
import com.kidsvocab.domain.game.GameTaskWord;
import com.kidsvocab.domain.game.GameTaskWordRepository;
import com.kidsvocab.domain.game.WordSearchGrid;
import com.kidsvocab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Period;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Game tasks, prepared by the parent: a game mode and a word list (typed in or suggested), given to the
 * child when ready. A given task is frozen; it can still be deleted.
 */
@Service
public class GameTaskService {

    /** Fewer words make no game. */
    public static final int MIN_WORDS = 3;

    private final ChildRepository children;
    private final GameTaskRepository tasks;
    private final GameTaskWordRepository taskWords;
    private final ParentWords parentWords;
    private final DictionaryQueries dictionary;
    private final WordSuggestionSource suggestions;
    private final WordSearchMaker wordSearch;
    private final WeekLifecycleService lifecycle;
    private final Clock clock;

    public GameTaskService(ChildRepository children, GameTaskRepository tasks, GameTaskWordRepository taskWords,
                           ParentWords parentWords, DictionaryQueries dictionary, WordSuggestionSource suggestions,
                           WordSearchMaker wordSearch, WeekLifecycleService lifecycle, Clock clock) {
        this.children = children;
        this.tasks = tasks;
        this.taskWords = taskWords;
        this.parentWords = parentWords;
        this.dictionary = dictionary;
        this.suggestions = suggestions;
        this.wordSearch = wordSearch;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /** A word-search grid holds fewer words than a list. */
    public static int maxWords(GameMode mode) {
        return mode == GameMode.WORD_SEARCH ? 8 : 12;
    }

    public record TaskDetails(GameTask task, List<GameTaskWord> words) {
    }

    @Transactional(readOnly = true)
    public List<TaskDetails> list(CurrentUser user, long childId) {
        Child child = ownChild(user, childId);
        return tasks.findByChildIdOrderByIdDesc(child.getId()).stream().map(this::details).toList();
    }

    @Transactional
    public TaskDetails create(CurrentUser user, long childId, GameMode mode) {
        Child child = ownChild(user, childId);
        return details(tasks.save(new GameTask(child.getId(), mode, clock.instant())));
    }

    @Transactional(readOnly = true)
    public TaskDetails get(CurrentUser user, long taskId) {
        return details(ownTask(user, taskId));
    }

    @Transactional
    public void delete(CurrentUser user, long taskId) {
        tasks.delete(ownTask(user, taskId));
    }

    @Transactional
    public TaskDetails addWord(CurrentUser user, long taskId, String source, String target) {
        GameTask task = draft(user, taskId);
        Child child = childOf(task);
        requireRoom(task);
        long conceptId = parentWords.conceptFor(child, user.parentId(), source, target, List.of());
        Card card = card(child, conceptId);
        if (!fitsTheGrid(task, card)) {
            throw new AppException(Code.VALIDATION,
                    "The word is too long for a word search (at most " + WordSearch.MAX_SIZE + " letters)");
        }
        add(child, task, card);
        return details(task);
    }

    @Transactional
    public TaskDetails removeWord(CurrentUser user, long taskId, long wordId) {
        GameTask task = draft(user, taskId);
        taskWords.delete(taskWords.findByIdAndTaskId(wordId, task.getId())
                .orElseThrow(() -> AppException.notFound("Word")));
        return details(task);
    }

    /** Adds suggested words (AI first, then the dictionary): {@code count} of them, or as many as fit. */
    @Transactional
    public TaskDetails fill(CurrentUser user, long taskId, String topic, Integer count) {
        GameTask task = draft(user, taskId);
        Child child = childOf(task);
        List<GameTaskWord> current = taskWords.findByTaskIdOrderById(task.getId());
        int room = maxWords(task.getMode()) - current.size();
        int wanted = count == null ? room : Math.min(count, room);
        if (wanted <= 0) {
            throw new AppException(Code.CONFLICT, "The task is full");
        }
        Set<Long> concepts = new HashSet<>();
        Set<String> prompts = new HashSet<>();
        Set<String> answers = new HashSet<>();
        for (GameTaskWord w : current) {
            concepts.add(w.getConceptId());
            prompts.add(AnswerNormalizer.normalize(w.getPromptText(), locale(child.getNativeLang())));
            answers.add(AnswerNormalizer.normalize(w.getAnswerText(), locale(child.getTargetLang())));
        }
        int added = 0;
        for (WordSuggestionSource.Suggestion s : suggestions.suggest(new WordSuggestionSource.Request(
                child.getParentId(), age(child), child.getLevel(), child.getNativeLang(), child.getTargetLang(),
                topic, wanted, concepts, prompts, answers))) {
            Card card = card(child, s.conceptId());
            if (fitsTheGrid(task, card) && !clashes(child, task, card)) {
                saveWord(task, card);
                added++;
            }
        }
        if (added == 0) {
            throw new AppException(Code.CONFLICT, "No words are available");
        }
        return details(task);
    }

    /** Hands the task to the child; a word search gets its grid now. */
    @Transactional
    public TaskDetails give(CurrentUser user, long taskId) {
        GameTask task = draft(user, taskId);
        Child child = childOf(task);
        List<GameTaskWord> words = taskWords.findByTaskIdOrderById(task.getId());
        if (words.size() < MIN_WORDS) {
            throw new AppException(Code.VALIDATION, "A game needs at least " + MIN_WORDS + " words");
        }
        WordSearchGrid grid = task.getMode() != GameMode.WORD_SEARCH ? null
                : wordSearch.make(words.stream().map(w -> new WordSearchMaker.Word(w.getId(), w.getAnswerText())).toList(),
                        child.getTargetLang(), child.getLevel() == Level.A2);
        task.give(grid, clock.instant());
        return details(task);
    }

    // --- internals -------------------------------------------------------------------------------

    private void add(Child child, GameTask task, Card card) {
        if (clashes(child, task, card)) {
            throw new AppException(Code.CONFLICT, "The task already has this word or one that looks the same");
        }
        saveWord(task, card);
    }

    private void saveWord(GameTask task, Card card) {
        taskWords.save(new GameTaskWord(task.getId(), card.conceptId(), card.prompt(), card.primaryAnswer()));
        taskWords.flush();
    }

    /** Same concept, same native word or same answer as a word already in the task. */
    private boolean clashes(Child child, GameTask task, Card card) {
        Locale nativeLocale = locale(child.getNativeLang());
        Locale targetLocale = locale(child.getTargetLang());
        String prompt = AnswerNormalizer.normalize(card.prompt(), nativeLocale);
        String answer = AnswerNormalizer.normalize(card.primaryAnswer(), targetLocale);
        return taskWords.findByTaskIdOrderById(task.getId()).stream().anyMatch(w ->
                w.getConceptId().equals(card.conceptId())
                        || AnswerNormalizer.normalize(w.getPromptText(), nativeLocale).equals(prompt)
                        || AnswerNormalizer.normalize(w.getAnswerText(), targetLocale).equals(answer));
    }

    private static boolean fitsTheGrid(GameTask task, Card card) {
        int letters = WordSearch.letters(card.primaryAnswer()).length();
        return task.getMode() != GameMode.WORD_SEARCH
                || (letters >= WordSearch.MIN_LETTERS && letters <= WordSearch.MAX_SIZE);
    }

    private void requireRoom(GameTask task) {
        if (taskWords.countByTaskId(task.getId()) >= maxWords(task.getMode())) {
            throw new AppException(Code.CONFLICT, "The task is full");
        }
    }

    private Card card(Child child, long conceptId) {
        return dictionary.card(conceptId, child.getNativeLang(), child.getTargetLang())
                .orElseThrow(() -> new IllegalStateException("concept " + conceptId + " has no forms for "
                        + child.getNativeLang() + "/" + child.getTargetLang()));
    }

    private GameTask draft(CurrentUser user, long taskId) {
        GameTask task = ownTask(user, taskId);
        if (task.getStatus() != GameTask.Status.DRAFT) {
            throw new AppException(Code.CONFLICT, "A given task cannot be changed");
        }
        return task;
    }

    private GameTask ownTask(CurrentUser user, long taskId) {
        GameTask task = tasks.findById(taskId).orElseThrow(() -> AppException.notFound("Task"));
        ownChild(user, task.getChildId());
        return task;
    }

    private Child ownChild(CurrentUser user, long childId) {
        if (!user.isParent()) {
            throw new AppException(Code.FORBIDDEN, "Parent mode required");
        }
        return children.findByIdAndParentIdAndArchivedFalse(childId, user.parentId())
                .orElseThrow(() -> AppException.notFound("Child"));
    }

    private Child childOf(GameTask task) {
        return children.findById(task.getChildId()).orElseThrow();
    }

    private TaskDetails details(GameTask task) {
        return new TaskDetails(task, taskWords.findByTaskIdOrderById(task.getId()));
    }

    private Integer age(Child child) {
        return child.getBirthDate() == null ? null
                : Period.between(child.getBirthDate(), lifecycle.today(child)).getYears();
    }

    private static Locale locale(String lang) {
        return Locale.forLanguageTag(lang);
    }
}
