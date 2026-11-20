package com.kidsvocab.app.week;

import com.kidsvocab.core.training.WordState;
import com.kidsvocab.domain.study.StudyWeek;
import com.kidsvocab.domain.study.WeekWord;

import java.util.List;

/** Reads how far the child got in the first pass of a week, one state per week word. */
public interface FirstPassProgress {

    List<WordState> wordStates(StudyWeek week, List<WeekWord> words);
}
