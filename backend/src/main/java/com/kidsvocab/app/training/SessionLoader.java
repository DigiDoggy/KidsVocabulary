package com.kidsvocab.app.training;

import com.kidsvocab.core.training.TrainingSession;
import com.kidsvocab.core.training.WordCard;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.study.SessionEvent;
import com.kidsvocab.domain.study.SessionEventRepository;
import com.kidsvocab.domain.study.SessionRecord;
import com.kidsvocab.domain.study.WeekWord;
import com.kidsvocab.domain.study.WeekWordRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Rebuilds a {@link TrainingSession} from its record and event log. */
@Component
public class SessionLoader {

    private final WeekWordRepository words;
    private final SessionEventRepository events;

    public SessionLoader(WeekWordRepository words, SessionEventRepository events) {
        this.words = words;
        this.events = events;
    }

    public TrainingSession load(SessionRecord record, Child child) {
        List<WordCard> cards = cards(record.getWeekId(), child);
        TrainingSession session = TrainingSession.start(cards, record.getSeed());
        for (SessionEvent event : events.findBySessionIdOrderBySeq(record.getId())) {
            try {
                if (event.getKind() == SessionEvent.Kind.HINT) {
                    session.hint(event.getWeekWordId(), event.getRoundNo());
                } else {
                    session.submit(event.getWeekWordId(), event.getRoundNo(), event.getAnswerText());
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException(
                        "event " + event.getSeq() + " of session " + record.getId() + " cannot be replayed", e);
            }
        }
        if (record.getStatus() == TrainingSession.Status.CLOSED) {
            session.close();
        }
        return session;
    }

    public List<WordCard> cards(long weekId, Child child) {
        Locale target = Locale.forLanguageTag(child.getTargetLang());
        return words.findByWeekIdOrderById(weekId).stream()
                .map(w -> toCard(w, target))
                .toList();
    }

    /** Pictures of the week's words by week word id; words without a picture are left out. */
    public Map<Long, String> emojis(long weekId) {
        Map<Long, String> result = new HashMap<>();
        for (WeekWord w : words.findByWeekIdOrderById(weekId)) {
            if (w.getEmoji() != null) {
                result.put(w.getId(), w.getEmoji());
            }
        }
        return result;
    }

    private static WordCard toCard(WeekWord w, Locale target) {
        List<String> accepted = w.getAcceptedAnswers();
        return WordCard.of(w.getId(), w.getPromptText(), w.getPrimaryAnswer(), accepted, target);
    }
}
