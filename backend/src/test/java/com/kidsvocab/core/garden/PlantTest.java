package com.kidsvocab.core.garden;

import com.kidsvocab.core.training.WordState;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlantTest {

    private static final LocalDate DAY = LocalDate.of(2027, 1, 17);

    @Test
    void aNewPlantIsASeedThatWantsWaterAWeekLater() {
        Plant p = Plant.seed(3, DAY);
        assertThat(p).isEqualTo(new Plant(3, GrowthStage.SEED, 1, DAY.plusWeeks(1)));
    }

    @Test
    void successfulReviewsGrowItWithDoublingWaits() {
        Plant p = Plant.seed(0, DAY);
        List<GrowthStage> stages = new ArrayList<>();
        List<Integer> waits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate today = p.nextReviewOn();
            p = p.reviewed(true, today);
            assertThat(p.nextReviewOn()).isEqualTo(today.plusWeeks(p.intervalWeeks()));
            stages.add(p.stage());
            waits.add(p.intervalWeeks());
        }
        assertThat(stages).containsExactly(GrowthStage.SPROUT, GrowthStage.YOUNG, GrowthStage.BLOOMING,
                GrowthStage.FRUIT, GrowthStage.FRUIT, GrowthStage.FRUIT);
        assertThat(waits).containsExactly(2, 4, 8, 16, 16, 16);
    }

    @Test
    void aFailedReviewKeepsTheStageAndAsksAgainTomorrow() {
        Plant young = new Plant(5, GrowthStage.YOUNG, 4, DAY);
        Plant failed = young.reviewed(false, DAY.plusDays(3));
        assertThat(failed).isEqualTo(new Plant(5, GrowthStage.YOUNG, 0, DAY.plusDays(4)));

        Plant grown = failed.reviewed(true, DAY.plusDays(4));
        assertThat(grown.stage()).isEqualTo(GrowthStage.BLOOMING);
        assertThat(grown.intervalWeeks()).isEqualTo(1);
        assertThat(grown.nextReviewOn()).isEqualTo(DAY.plusDays(4).plusWeeks(1));
    }

    @Test
    void dueOnItsDayAndThirstyAfterIt() {
        Plant p = new Plant(1, GrowthStage.SPROUT, 2, DAY);
        assertThat(p.isDue(DAY.minusDays(1))).isFalse();
        assertThat(p.isThirsty(DAY.minusDays(1))).isFalse();
        assertThat(p.isDue(DAY)).isTrue();
        assertThat(p.isThirsty(DAY)).isFalse();
        assertThat(p.isDue(DAY.plusDays(1))).isTrue();
        assertThat(p.isThirsty(DAY.plusDays(1))).isTrue();
    }

    @Test
    void onlyAFirstTryWithoutHintCountsAsSuccess() {
        assertThat(Plant.succeeded(new WordState(1, 0, false, false, 1))).isTrue();
        assertThat(Plant.succeeded(new WordState(1, 0, true, false, 1))).isFalse();
        assertThat(Plant.succeeded(new WordState(1, 1, false, false, 2))).isFalse();
        assertThat(Plant.succeeded(WordState.notStarted(1))).isFalse();
    }

    @Test
    void rejectsImpossibleValues() {
        assertThatThrownBy(() -> new Plant(-1, GrowthStage.SEED, 1, DAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plant(Plant.KINDS, GrowthStage.SEED, 1, DAY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plant(0, GrowthStage.SEED, -1, DAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plant(0, GrowthStage.SEED, 17, DAY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Plant(0, null, 1, DAY)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Plant(0, GrowthStage.SEED, 1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void stagesByCode() {
        assertThat(GrowthStage.ofCode(0)).isEqualTo(GrowthStage.SEED);
        assertThat(GrowthStage.ofCode(4)).isEqualTo(GrowthStage.FRUIT);
        assertThatThrownBy(() -> GrowthStage.ofCode(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GrowthStage.ofCode(5)).isInstanceOf(IllegalArgumentException.class);
        assertThat(GrowthStage.SEED.next()).isEqualTo(GrowthStage.SPROUT);
        assertThat(GrowthStage.FRUIT.next()).isEqualTo(GrowthStage.FRUIT);
    }
}
