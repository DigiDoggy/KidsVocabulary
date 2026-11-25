package com.kidsvocab.web.garden;

import com.kidsvocab.app.garden.GardenService;
import com.kidsvocab.app.garden.GardenService.GardenView;
import com.kidsvocab.app.training.TrainingService;
import com.kidsvocab.app.training.TrainingService.SessionView;
import com.kidsvocab.core.garden.GrowthStage;
import com.kidsvocab.domain.study.GardenRepository.GardenPlant;
import com.kidsvocab.security.CurrentUser;
import com.kidsvocab.web.training.TrainingController.SessionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The child's word garden. Watering it is a training session; answers go to {@code /sessions/{id}/...}. */
@RestController
@RequestMapping("/api/v1/me/garden")
@PreAuthorize("hasAuthority('MODE_CHILD')")
public class GardenController {

    private final GardenService garden;
    private final TrainingService training;

    public GardenController(GardenService garden, TrainingService training) {
        this.garden = garden;
        this.training = training;
    }

    /**
     * @param dueCount     plants that want water today
     * @param reviewSize   how many of them one watering takes
     * @param openReviewId an unfinished watering, or {@code null}
     * @param beds         one bed per study month the words were learned in, oldest first
     */
    public record GardenResponse(int dueCount, int reviewSize, Long openReviewId, int plantCount, List<Bed> beds) {

        static GardenResponse of(GardenView v) {
            Map<Integer, List<PlantView>> byMonth = new LinkedHashMap<>();
            for (GardenPlant p : v.plants()) {
                byMonth.computeIfAbsent(p.monthSeq() == null ? -1 : p.monthSeq(), k -> new ArrayList<>())
                        .add(PlantView.of(p, v.today()));
            }
            List<Bed> beds = byMonth.entrySet().stream()
                    .map(e -> new Bed(e.getKey() < 0 ? null : e.getKey(), e.getValue()))
                    .toList();
            return new GardenResponse(v.dueCount(), v.reviewSize(), v.openReviewId(), v.plants().size(), beds);
        }
    }

    /** @param month the study month's number, {@code null} if unknown */
    public record Bed(Integer month, List<PlantView> plants) {
    }

    /**
     * @param kind    which drawing, 0..7
     * @param due     wants water today
     * @param thirsty was not watered on its day (drooping)
     */
    public record PlantView(long conceptId, int kind, GrowthStage stage, boolean due, boolean thirsty,
                            String prompt, String answer, String emoji) {

        static PlantView of(GardenPlant p, LocalDate today) {
            return new PlantView(p.conceptId(), p.plant().kind(), p.plant().stage(), p.plant().isDue(today),
                    p.plant().isThirsty(today), p.prompt(), p.answer(), p.emoji());
        }
    }

    @GetMapping
    public GardenResponse garden(CurrentUser user) {
        return GardenResponse.of(garden.garden(user));
    }

    /** Starts watering (or continues the unfinished one); 409 when no plant needs water. */
    @PostMapping("/reviews")
    public SessionResponse water(CurrentUser user) {
        long sessionId = Objects.requireNonNull(garden.startOrResumeReview(user).getId());
        SessionView view = training.get(user, sessionId);
        return SessionResponse.of(view, training.emojis(view.record()));
    }
}
