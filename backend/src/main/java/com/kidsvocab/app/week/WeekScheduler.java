package com.kidsvocab.app.week;

import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Periodically expires finished weeks, activates scheduled ones and prepares automatic drafts.
 * Request handlers also bring a child's weeks up to date, so the interval only affects children
 * nobody is looking at. Disabled in tests via {@code app.scheduling.enabled=false}.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class WeekScheduler {

    private static final Logger log = LoggerFactory.getLogger(WeekScheduler.class);

    private final ChildRepository children;
    private final WeekService weeks;

    public WeekScheduler(ChildRepository children, WeekService weeks) {
        this.children = children;
        this.weeks = weeks;
    }

    @Scheduled(fixedDelayString = "${app.scheduling.week-lifecycle-interval:PT15M}", initialDelayString = "PT30S")
    public void run() {
        for (Child child : children.findAll()) {
            if (child.isArchived()) {
                continue;
            }
            try {
                weeks.runLifecycle(child.getId());
            } catch (RuntimeException e) {
                log.error("Week lifecycle failed for child {}", child.getId(), e);
            }
        }
    }
}
