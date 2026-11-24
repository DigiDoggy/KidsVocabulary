package com.kidsvocab.app.child;

import com.kidsvocab.app.AppException;
import com.kidsvocab.app.AppException.Code;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.ChildRepository;
import com.kidsvocab.domain.child.Gender;
import com.kidsvocab.domain.child.Level;
import com.kidsvocab.domain.language.LanguageRepository;
import com.kidsvocab.domain.study.StudyWeekRepository;
import com.kidsvocab.domain.study.WeekStatus;
import com.kidsvocab.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class ChildService {

    public static final int DEFAULT_WEEK_SIZE = 10;
    private static final List<WeekStatus> OPEN_WEEKS =
            List.of(WeekStatus.DRAFT, WeekStatus.SCHEDULED, WeekStatus.ACTIVE);

    private final ChildRepository children;
    private final LanguageRepository languages;
    private final StudyWeekRepository weeks;
    private final Clock clock;

    public ChildService(ChildRepository children, LanguageRepository languages, StudyWeekRepository weeks,
                        Clock clock) {
        this.children = children;
        this.languages = languages;
        this.weeks = weeks;
        this.clock = clock;
    }

    /** Profile data as entered by the parent. */
    public record ChildData(String name, LocalDate birthDate, Level level, Gender gender, String nativeLang,
                            String targetLang, Integer defaultWeekSize) {
    }

    @Transactional(readOnly = true)
    public List<Child> list(CurrentUser user) {
        requireParent(user);
        return children.findByParentIdAndArchivedFalseOrderById(user.parentId());
    }

    @Transactional(readOnly = true)
    public Child get(CurrentUser user, long childId) {
        if (!user.canView(childId)) {
            throw AppException.notFound("Child");
        }
        return find(user.parentId(), childId);
    }

    @Transactional
    public Child create(CurrentUser user, ChildData data) {
        requireParent(user);
        Child child = new Child(user.parentId(), clock.instant());
        apply(child, data);
        return children.save(child);
    }

    @Transactional
    public Child update(CurrentUser user, long childId, ChildData data) {
        requireParent(user);
        Child child = find(user.parentId(), childId);
        boolean languagesChange = !data.nativeLang().equals(child.getNativeLang())
                || !data.targetLang().equals(child.getTargetLang());
        if (languagesChange && weeks.existsByChildIdAndStatusIn(child.getId(), OPEN_WEEKS)) {
            throw new AppException(Code.CONFLICT, "Languages cannot change while a week is prepared or running");
        }
        apply(child, data);
        return child;
    }

    /** Profiles are archived, not deleted, so their history stays intact. */
    @Transactional
    public void archive(CurrentUser user, long childId) {
        requireParent(user);
        find(user.parentId(), childId).archive();
    }

    private Child find(long parentId, long childId) {
        return children.findByIdAndParentIdAndArchivedFalse(childId, parentId)
                .orElseThrow(() -> AppException.notFound("Child"));
    }

    private void apply(Child child, ChildData data) {
        if (data.nativeLang().equals(data.targetLang())) {
            throw new AppException(Code.VALIDATION, "Native and target language must differ");
        }
        requireLanguage(data.nativeLang());
        requireLanguage(data.targetLang());
        int weekSize = data.defaultWeekSize() == null ? DEFAULT_WEEK_SIZE : data.defaultWeekSize();
        child.update(data.name().strip(), data.birthDate(), data.level(), data.gender(), data.nativeLang(),
                data.targetLang(), weekSize);
    }

    private void requireLanguage(String code) {
        if (!languages.existsById(code)) {
            throw new AppException(Code.VALIDATION, "Unsupported language: " + code);
        }
    }

    private static void requireParent(CurrentUser user) {
        if (!user.isParent()) {
            throw new AppException(Code.FORBIDDEN, "Parent mode required");
        }
    }
}
