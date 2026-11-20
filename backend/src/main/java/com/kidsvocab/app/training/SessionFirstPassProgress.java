package com.kidsvocab.app.training;

import com.kidsvocab.app.week.FirstPassProgress;
import com.kidsvocab.core.training.WordState;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.study.SessionRecord;
import com.kidsvocab.domain.study.SessionRecordRepository;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.WeekWord;
import org.springframework.stereotype.Component;

import java.util.List;

/** First-pass progress read from the stored session; a week never started counts as not started. */
@Component
public class SessionFirstPassProgress implements FirstPassProgress {

    private final SessionRecordRepository sessions;
    private final SessionLoader loader;
    private final ChildRepository children;

    public SessionFirstPassProgress(SessionRecordRepository sessions, SessionLoader loader,
                                    ChildRepository children) {
        this.sessions = sessions;
        this.loader = loader;
        this.children = children;
    }

    @Override
    public List<WordState> wordStates(StudyWeek week, List<WeekWord> words) {
        return sessions.findByWeekIdAndKind(week.getId(), SessionRecord.Kind.FIRST_PASS)
                .map(record -> loader.load(record, children.findById(week.getChildId()).orElseThrow()).wordStates())
                .orElseGet(() -> words.stream().map(w -> WordState.notStarted(w.getId())).toList());
    }
}
