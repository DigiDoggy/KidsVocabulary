package com.kidsvocab.web.week;

import com.kidsvocab.app.week.WeekService;
import com.kidsvocab.security.CurrentUser;
import com.kidsvocab.web.week.WeekDtos.AddWordRequest;
import com.kidsvocab.web.week.WeekDtos.CreateWeekRequest;
import com.kidsvocab.web.week.WeekDtos.TopicRequest;
import com.kidsvocab.web.week.WeekDtos.UpdateWeekRequest;
import com.kidsvocab.web.week.WeekDtos.WeekResponse;
import com.kidsvocab.web.week.WeekDtos.WeekSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;
import java.util.List;

/** Weekly word sets, managed by the parent. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAuthority('MODE_PARENT')")
public class WeekController {

    private final WeekService weeks;

    public WeekController(WeekService weeks) {
        this.weeks = weeks;
    }

    @GetMapping("/children/{childId}/weeks")
    public List<WeekSummary> list(CurrentUser user, @PathVariable long childId) {
        return weeks.list(user, childId).stream().map(WeekSummary::of).toList();
    }

    @PostMapping("/children/{childId}/weeks")
    public ResponseEntity<WeekResponse> create(CurrentUser user, @PathVariable long childId,
                                               @Valid @RequestBody(required = false) CreateWeekRequest request) {
        CreateWeekRequest r = request == null ? new CreateWeekRequest(null, null) : request;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(WeekResponse.of(weeks.createDraft(user, childId, r.startDate(), r.wordCount())));
    }

    @GetMapping("/weeks/{weekId}")
    public WeekResponse get(CurrentUser user, @PathVariable long weekId) {
        return WeekResponse.of(weeks.get(user, weekId));
    }

    @PutMapping("/weeks/{weekId}")
    public WeekResponse update(CurrentUser user, @PathVariable long weekId,
                               @Valid @RequestBody UpdateWeekRequest request) {
        return WeekResponse.of(weeks.updateDraft(user, weekId, request.startDate(), request.wordCount()));
    }

    @DeleteMapping("/weeks/{weekId}")
    public ResponseEntity<Void> delete(CurrentUser user, @PathVariable long weekId) {
        weeks.deleteDraft(user, weekId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/weeks/{weekId}/words")
    public WeekResponse addWord(CurrentUser user, @PathVariable long weekId,
                                @Valid @RequestBody AddWordRequest request) {
        return WeekResponse.of(weeks.addManualWord(user, weekId, request.source(), request.target(),
                request.extraAnswers()));
    }

    @DeleteMapping("/weeks/{weekId}/words/{wordId}")
    public WeekResponse removeWord(CurrentUser user, @PathVariable long weekId, @PathVariable long wordId) {
        return WeekResponse.of(weeks.removeWord(user, weekId, wordId));
    }

    @PostMapping("/weeks/{weekId}/words/{wordId}/replace")
    public WeekResponse replaceWord(CurrentUser user, @PathVariable long weekId, @PathVariable long wordId,
                                    @Valid @RequestBody(required = false) TopicRequest request) {
        return WeekResponse.of(weeks.replaceWord(user, weekId, wordId, topic(request)));
    }

    @PostMapping("/weeks/{weekId}/suggest")
    public WeekResponse fillUp(CurrentUser user, @PathVariable long weekId,
                               @Valid @RequestBody(required = false) TopicRequest request) {
        return WeekResponse.of(weeks.fillUp(user, weekId, topic(request)));
    }

    @PostMapping("/weeks/{weekId}/launch")
    public WeekResponse launch(CurrentUser user, @PathVariable long weekId) {
        return WeekResponse.of(weeks.launch(user, weekId));
    }

    @PostMapping("/weeks/{weekId}/unschedule")
    public WeekResponse unschedule(CurrentUser user, @PathVariable long weekId) {
        return WeekResponse.of(weeks.unschedule(user, weekId));
    }

    private static String topic(TopicRequest request) {
        return request == null || request.topic() == null || request.topic().isBlank()
                ? null : request.topic().strip().replaceAll(" +", " ").toLowerCase(Locale.ROOT);
    }
}
