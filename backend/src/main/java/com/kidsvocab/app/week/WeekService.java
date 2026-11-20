package com.kidsvocab.app.week;

import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.core.month.CarryOverPlanner;
import com.kidsvocab.core.text.AnswerNormalizer;
import com.kidsvocab.core.week.WeekCalendar;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.dictionary.Concept;
import com.kidsvocab.domain.dictionary.ConceptRepository;
import com.kidsvocab.domain.dictionary.DictionaryQueries;
import com.kidsvocab.domain.dictionary.DictionaryQueries.Card;
import com.kidsvocab.domain.dictionary.WordForm;
import com.kidsvocab.domain.dictionary.WordFormRepository;
import com.kidsvocab.domain.study.CarryOver;
import com.kidsvocab.domain.study.CarryOverRepository;
import com.kidsvocab.domain.study.StudyMonth;
import com.kidsvocab.domain.study.StudyMonthRepository;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.StudyWeekRepository;
import com.kidsvocab.domain.study.WeekStatus;
import com.kidsvocab.domain.study.WeekWord;
import com.kidsvocab.domain.study.WeekWordRepository;
import com.kidsvocab.domain.study.WordSource;
import com.kidsvocab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Weekly word sets: drafts for this or next week, manual words, suggestions, carried-over words
 * and launching. A child has at most one prepared (draft or scheduled) week at a time.
 */
@Service
public class WeekService {

    private static final List<WeekStatus> PENDING = List.of(WeekStatus.DRAFT, WeekStatus.SCHEDULED);

    private final ChildRepository children;
    private final StudyWeekRepository weeks;
    private final WeekWordRepository words;
    private final StudyMonthRepository months;
    private final CarryOverRepository carryOvers;
    private final ConceptRepository concepts;
    private final WordFormRepository forms;
    private final DictionaryQueries dictionary;
    private final WordSuggestionSource suggestions;
    private final WeekLifecycleService lifecycle;
    private final Clock clock;

    public WeekService(ChildRepository children, StudyWeekRepository weeks, WeekWordRepository words,
                       StudyMonthRepository months, CarryOverRepository carryOvers, ConceptRepository concepts,
                       WordFormRepository forms, DictionaryQueries dictionary, WordSuggestionSource suggestions,
                       WeekLifecycleService lifecycle, Clock clock) {
        this.children = children;
        this.weeks = weeks;
        this.words = words;
        this.months = months;
        this.carryOvers = carryOvers;
        this.concepts = concepts;
        this.forms = forms;
        this.dictionary = dictionary;
        this.suggestions = suggestions;
        this.lifecycle = lifecycle;
        this.clock = clock;
    }

    /** A week with its words. */
    public record WeekDetails(StudyWeek week, List<WeekWord> words) {

        public int missing() {
            return Math.max(0, week.getWordCount() - words.size());
        }
    }

    // --- reading ---------------------------------------------------------------------------------

    @Transactional
    public List<StudyWeek> list(CurrentUser user, long childId) {
        Child child = ownChild(user, childId);
        lifecycle.refresh(child);
        return weeks.findByChildIdOrderByStartDateDescIdDesc(child.getId());
    }

    @Transactional
    public WeekDetails get(CurrentUser user, long weekId) {
        StudyWeek week = ownWeek(user, weekId);
        lifecycle.refresh(childOf(week));
        return details(week);
    }

    // --- drafts ----------------------------------------------------------------------------------

    /**
     * Creates a draft for this Monday or next Monday (default: this week if it has no set yet).
     * Carried-over words are placed into it straight away.
     */
    @Transactional
    public WeekDetails createDraft(CurrentUser user, long childId, LocalDate startDate, Integer wordCount) {
        Child child = ownChild(user, childId);
        lifecycle.refresh(child);
        if (weeks.existsByChildIdAndStatusIn(child.getId(), PENDING)) {
            throw new AppException(Code.CONFLICT, "A prepared week already exists");
        }
        LocalDate monday = resolveStart(child, startDate);
        StudyWeek week = weeks.save(new StudyWeek(child.getId(), monday,
                wordCount == null ? child.getDefaultWeekSize() : wordCount));
        placeCarryOver(child, week);
        return details(week);
    }

    @Transactional
    public WeekDetails updateDraft(CurrentUser user, long weekId, LocalDate startDate, int wordCount) {
        StudyWeek week = draft(user, weekId);
        Child child = childOf(week);
        if (startDate != null && !startDate.equals(week.getStartDate())) {
            week.reschedule(resolveStart(child, startDate));
        }
        if (wordCount < words.countByWeekId(week.getId())) {
            throw new AppException(Code.CONFLICT, "The set has more words than that; remove some first");
        }
        week.changeWordCount(wordCount);
        return details(week);
    }

    @Transactional
    public void deleteDraft(CurrentUser user, long weekId) {
        weeks.delete(draft(user, weekId));
    }

    // --- words -----------------------------------------------------------------------------------

    /** Adds a word typed by the parent: native word plus its translation and optional extra spellings. */
    @Transactional
    public WeekDetails addManualWord(CurrentUser user, long weekId, String source, String target,
                                     List<String> extraAnswers) {
        StudyWeek week = draft(user, weekId);
        Child child = childOf(week);
        requireRoom(week);
        String nativeNorm = AnswerNormalizer.normalize(source, locale(child.getNativeLang()));
        String targetNorm = AnswerNormalizer.normalize(target, locale(child.getTargetLang()));
        if (nativeNorm.isEmpty() || targetNorm.isEmpty()) {
            throw new AppException(Code.VALIDATION, "Both the word and its translation are required");
        }
        if (nativeNorm.equals(targetNorm)) {
            throw new AppException(Code.VALIDATION, "The translation must differ from the word");
        }
        long conceptId = dictionary.findPair(child.getNativeLang(), nativeNorm, child.getTargetLang(), targetNorm,
                user.parentId()).orElseGet(() -> createManualConcept(child, source, target, extraAnswers));
        Card card = card(child, conceptId);
        Set<String> accepted = new LinkedHashSet<>(card.acceptedAnswers());
        for (String extra : extraAnswers == null ? List.<String>of() : extraAnswers) {
            String normalized = AnswerNormalizer.normalize(extra, locale(child.getTargetLang()));
            if (!normalized.isEmpty()) {
                accepted.add(normalized);
            }
        }
        addCard(child, week, new Card(conceptId, card.prompt(), card.primaryAnswer(), List.copyOf(accepted)),
                WordSource.MANUAL);
        return details(week);
    }

    @Transactional
    public WeekDetails removeWord(CurrentUser user, long weekId, long wordId) {
        StudyWeek week = draft(user, weekId);
        words.delete(removableWord(week, wordId));
        return details(week);
    }

    /** Swaps a word for another suggestion. */
    @Transactional
    public WeekDetails replaceWord(CurrentUser user, long weekId, long wordId, String topic) {
        StudyWeek week = draft(user, weekId);
        Child child = childOf(week);
        WeekWord old = removableWord(week, wordId);
        List<WordSuggestionSource.Suggestion> found = suggest(child, week, topic, 1, Set.of(old.getConceptId()));
        if (found.isEmpty()) {
            throw new AppException(Code.CONFLICT, "No other words are available");
        }
        words.delete(old);
        words.flush();
        addSuggestion(child, week, found.getFirst());
        return details(week);
    }

    /** Fills the set up to its word count with suggestions. */
    @Transactional
    public WeekDetails fillUp(CurrentUser user, long weekId, String topic) {
        StudyWeek week = draft(user, weekId);
        fill(childOf(week), week, topic);
        return details(week);
    }

    // --- launching -------------------------------------------------------------------------------

    /** Freezes the set: a week for this Monday starts now, a week for next Monday is scheduled. */
    @Transactional
    public WeekDetails launch(CurrentUser user, long weekId) {
        StudyWeek week = draft(user, weekId);
        Child child = childOf(week);
        long count = words.countByWeekId(week.getId());
        if (count != week.getWordCount()) {
            throw new AppException(Code.VALIDATION,
                    "The set needs " + week.getWordCount() + " words, it has " + count);
        }
        LocalDate thisMonday = WeekCalendar.weekStart(lifecycle.today(child));
        if (week.getStartDate().isBefore(thisMonday)) {
            throw new AppException(Code.CONFLICT, "This week is already over; choose another week");
        }
        if (weeks.existsByChildIdAndStartDateAndStatusNot(child.getId(), week.getStartDate(), WeekStatus.DRAFT)) {
            throw new AppException(Code.CONFLICT, "That week already has a set");
        }
        if (week.getStartDate().equals(thisMonday)) {
            lifecycle.activate(week);
        } else {
            week.schedule(clock.instant());
        }
        return details(week);
    }

    /** Takes a scheduled week back into editing. */
    @Transactional
    public WeekDetails unschedule(CurrentUser user, long weekId) {
        StudyWeek week = ownWeek(user, weekId);
        if (week.getStatus() != WeekStatus.SCHEDULED) {
            throw new AppException(Code.CONFLICT, "Only a scheduled week can be taken back");
        }
        week.unschedule();
        return details(week);
    }

    // --- automatic draft -------------------------------------------------------------------------

    /**
     * Prepares this week's set when the parent has not: once per week, only for children who have
     * already studied, and only if nothing is prepared or running for this week.
     */
    @Transactional
    public void runLifecycle(long childId) {
        Child child = children.findById(childId).orElseThrow();
        if (child.isArchived()) {
            return;
        }
        lifecycle.refresh(child);
        LocalDate thisMonday = WeekCalendar.weekStart(lifecycle.today(child));
        boolean alreadyDone = child.getLastAutoDraft() != null && !child.getLastAutoDraft().isBefore(thisMonday);
        boolean hasStudied = weeks.existsByChildIdAndMonthIdIsNotNull(child.getId());
        if (alreadyDone || !hasStudied
                || weeks.existsByChildIdAndStatusIn(child.getId(), PENDING)
                || weeks.existsByChildIdAndStartDateAndStatusNot(child.getId(), thisMonday, WeekStatus.DRAFT)) {
            return;
        }
        StudyWeek week = weeks.save(new StudyWeek(child.getId(), thisMonday, child.getDefaultWeekSize()));
        placeCarryOver(child, week);
        fill(child, week, null);
        child.markAutoDraft(thisMonday);
    }

    // --- internals -------------------------------------------------------------------------------

    private LocalDate resolveStart(Child child, LocalDate requested) {
        LocalDate thisMonday = WeekCalendar.weekStart(lifecycle.today(child));
        LocalDate nextMonday = thisMonday.plusWeeks(1);
        if (requested == null) {
            return weekTaken(child, thisMonday) ? nextMonday : thisMonday;
        }
        if (!requested.equals(thisMonday) && !requested.equals(nextMonday)) {
            throw new AppException(Code.VALIDATION, "A week can start this Monday or next Monday");
        }
        if (weekTaken(child, requested)) {
            throw new AppException(Code.CONFLICT, "That week already has a set");
        }
        return requested;
    }

    private boolean weekTaken(Child child, LocalDate monday) {
        return weeks.existsByChildIdAndStartDateAndStatusNot(child.getId(), monday, WeekStatus.DRAFT);
    }

    private void placeCarryOver(Child child, StudyWeek week) {
        Map<Long, CarryOver> byConcept = new LinkedHashMap<>();
        for (CarryOver c : carryOvers.findByChildIdAndPlacedWeekIdIsNullOrderById(child.getId())) {
            if (dictionary.card(c.getConceptId(), child.getNativeLang(), child.getTargetLang()).isPresent()) {
                byConcept.putIfAbsent(c.getConceptId(), c);
            }
        }
        List<CarryOverPlanner.Candidate> queue = byConcept.values().stream()
                .map(c -> new CarryOverPlanner.Candidate(c.getConceptId(), c.getReason()))
                .toList();
        for (CarryOverPlanner.Candidate pick : CarryOverPlanner.select(queue, week.getWordCount(),
                lifecycle.weeksLeftForNextWeek(child.getId()))) {
            addCard(child, week, card(child, pick.conceptId()), WordSource.CARRY_OVER);
            byConcept.get(pick.conceptId()).placeInto(week.getId());
        }
    }

    private void fill(Child child, StudyWeek week, String topic) {
        int missing = week.getWordCount() - (int) words.countByWeekId(week.getId());
        for (WordSuggestionSource.Suggestion s : suggest(child, week, topic, missing, Set.of())) {
            addSuggestion(child, week, s);
        }
    }

    private List<WordSuggestionSource.Suggestion> suggest(Child child, StudyWeek week, String topic, int count,
                                                          Set<Long> alsoExclude) {
        if (count <= 0) {
            return List.of();
        }
        Set<Long> excludeConcepts = new HashSet<>(dictionary.conceptsToAvoid(child.getId()));
        excludeConcepts.addAll(alsoExclude);
        months.findByChildIdAndStatus(child.getId(), StudyMonth.Status.ACTIVE)
                .ifPresent(m -> excludeConcepts.addAll(dictionary.conceptsInMonth(m.getId())));
        Set<String> prompts = new HashSet<>();
        Set<String> answers = new HashSet<>();
        for (WeekWord w : words.findByWeekIdOrderById(week.getId())) {
            prompts.add(AnswerNormalizer.normalize(w.getPromptText(), locale(child.getNativeLang())));
            answers.add(w.getAcceptedAnswers().getFirst());
        }
        return suggestions.suggest(new WordSuggestionSource.Request(child.getParentId(), age(child),
                child.getLevel(), child.getNativeLang(), child.getTargetLang(), topic, count,
                excludeConcepts, prompts, answers));
    }

    private void addSuggestion(Child child, StudyWeek week, WordSuggestionSource.Suggestion suggestion) {
        addCard(child, week, card(child, suggestion.conceptId()), suggestion.source());
    }

    /** Adds a word after checking that the set has room and stays unambiguous. */
    private void addCard(Child child, StudyWeek week, Card card, WordSource source) {
        requireRoom(week);
        Locale nativeLocale = locale(child.getNativeLang());
        String prompt = AnswerNormalizer.normalize(card.prompt(), nativeLocale);
        String answer = card.acceptedAnswers().getFirst();
        for (WeekWord existing : words.findByWeekIdOrderById(week.getId())) {
            if (existing.getConceptId().equals(card.conceptId())) {
                throw new AppException(Code.CONFLICT, "This word is already in the set");
            }
            if (AnswerNormalizer.normalize(existing.getPromptText(), nativeLocale).equals(prompt)
                    || existing.getAcceptedAnswers().getFirst().equals(answer)) {
                throw new AppException(Code.CONFLICT, "The set already has a word that looks the same");
            }
        }
        if (source == WordSource.MANUAL) {
            months.findByChildIdAndStatus(child.getId(), StudyMonth.Status.ACTIVE)
                    .filter(m -> dictionary.conceptsInMonth(m.getId()).contains(card.conceptId()))
                    .ifPresent(m -> {
                        throw new AppException(Code.CONFLICT, "This word was already studied this month");
                    });
        }
        words.save(new WeekWord(week.getId(), card.conceptId(), source, card.prompt(), card.primaryAnswer(),
                card.acceptedAnswers()));
        words.flush();
    }

    private long createManualConcept(Child child, String source, String target, List<String> extraAnswers) {
        Locale nativeLocale = locale(child.getNativeLang());
        Locale targetLocale = locale(child.getTargetLang());
        String targetText = target.strip();
        Concept concept = concepts.save(new Concept(null, Concept.difficultyOf(targetText), Concept.Origin.MANUAL,
                child.getParentId(), clock.instant()));
        forms.save(new WordForm(concept.getId(), child.getNativeLang(), source.strip(),
                AnswerNormalizer.normalize(source, nativeLocale), true));
        String primary = AnswerNormalizer.normalize(target, targetLocale);
        forms.save(new WordForm(concept.getId(), child.getTargetLang(), targetText, primary, true));
        Set<String> seen = new HashSet<>(Set.of(primary));
        for (String extra : extraAnswers == null ? List.<String>of() : extraAnswers) {
            String normalized = AnswerNormalizer.normalize(extra, targetLocale);
            if (!normalized.isEmpty() && seen.add(normalized)) {
                forms.save(new WordForm(concept.getId(), child.getTargetLang(), extra.strip(), normalized, false));
            }
        }
        forms.flush();
        return concept.getId();
    }

    private Card card(Child child, long conceptId) {
        return dictionary.card(conceptId, child.getNativeLang(), child.getTargetLang())
                .orElseThrow(() -> new IllegalStateException(
                        "concept " + conceptId + " has no forms for " + child.getNativeLang() + "/" + child.getTargetLang()));
    }

    private WeekWord removableWord(StudyWeek week, long wordId) {
        WeekWord word = words.findByIdAndWeekId(wordId, week.getId()).orElseThrow(() -> AppException.notFound("Word"));
        if (word.getSource() == WordSource.CARRY_OVER) {
            throw new AppException(Code.CONFLICT, "Words carried over from last month stay in the set");
        }
        return word;
    }

    private void requireRoom(StudyWeek week) {
        if (words.countByWeekId(week.getId()) >= week.getWordCount()) {
            throw new AppException(Code.CONFLICT, "The set is full");
        }
    }

    private StudyWeek draft(CurrentUser user, long weekId) {
        StudyWeek week = ownWeek(user, weekId);
        if (week.getStatus() != WeekStatus.DRAFT) {
            throw new AppException(Code.CONFLICT, "Only a draft can be changed");
        }
        return week;
    }

    private StudyWeek ownWeek(CurrentUser user, long weekId) {
        requireParent(user);
        StudyWeek week = weeks.findById(weekId).orElseThrow(() -> AppException.notFound("Week"));
        ownChild(user, week.getChildId());
        return week;
    }

    private Child ownChild(CurrentUser user, long childId) {
        requireParent(user);
        return children.findByIdAndParentIdAndArchivedFalse(childId, user.parentId())
                .orElseThrow(() -> AppException.notFound("Child"));
    }

    private Child childOf(StudyWeek week) {
        return children.findById(week.getChildId()).orElseThrow();
    }

    private WeekDetails details(StudyWeek week) {
        return new WeekDetails(week, new ArrayList<>(words.findByWeekIdOrderById(week.getId())));
    }

    private Integer age(Child child) {
        return child.getBirthDate() == null ? null
                : Period.between(child.getBirthDate(), lifecycle.today(child)).getYears();
    }

    private static Locale locale(String lang) {
        return Locale.forLanguageTag(lang);
    }

    private static void requireParent(CurrentUser user) {
        if (!user.isParent()) {
            throw new AppException(Code.FORBIDDEN, "Parent mode required");
        }
    }
}
