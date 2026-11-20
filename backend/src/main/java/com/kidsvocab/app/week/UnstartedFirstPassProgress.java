package com.kidsvocab.app.week;

import com.kidsvocab.core.training.WordState;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.WeekWord;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Progress source used until training sessions are persisted: every word counts as not started,
 * so an expired week scores 0 and all its words move to the next month.
 */
@Component
public class UnstartedFirstPassProgress implements FirstPassProgress {

    @Override
    public List<WordState> wordStates(StudyWeek week, List<WeekWord> words) {
        return words.stream().map(w -> WordState.notStarted(w.getId())).toList();
    }
}
