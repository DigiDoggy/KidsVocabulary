package com.kidsvocab.web.child;

import com.kidsvocab.app.child.ChildService;
import com.kidsvocab.app.child.ChildService.ChildData;
import com.kidsvocab.domain.child.Child;
import com.kidsvocab.domain.child.Gender;
import com.kidsvocab.domain.child.Level;
import com.kidsvocab.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
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

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/children")
public class ChildController {

    private final ChildService children;

    public ChildController(ChildService children) {
        this.children = children;
    }

    public record ChildRequest(
            @NotBlank @Size(max = 50) String name,
            @Past LocalDate birthDate,
            @NotNull Level level,
            Gender gender,
            @NotBlank @Size(max = 5) String nativeLang,
            @NotBlank @Size(max = 5) String targetLang,
            @Min(1) @Max(50) Integer defaultWeekSize) {

        ChildData toData() {
            return new ChildData(name, birthDate, level, gender, nativeLang, targetLang, defaultWeekSize);
        }
    }

    public record ChildResponse(long id, String name, LocalDate birthDate, Level level, Gender gender,
                                String nativeLang, String targetLang, int defaultWeekSize) {

        static ChildResponse of(Child c) {
            return new ChildResponse(c.getId(), c.getName(), c.getBirthDate(), c.getLevel(), c.getGender(),
                    c.getNativeLang(), c.getTargetLang(), c.getDefaultWeekSize());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public List<ChildResponse> list(CurrentUser user) {
        return children.list(user).stream().map(ChildResponse::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public ResponseEntity<ChildResponse> create(CurrentUser user, @Valid @RequestBody ChildRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ChildResponse.of(children.create(user, request.toData())));
    }

    /** Available in child mode too, for the child's own profile. */
    @GetMapping("/{id}")
    public ChildResponse get(CurrentUser user, @PathVariable long id) {
        return ChildResponse.of(children.get(user, id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public ChildResponse update(CurrentUser user, @PathVariable long id, @Valid @RequestBody ChildRequest request) {
        return ChildResponse.of(children.update(user, id, request.toData()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MODE_PARENT')")
    public ResponseEntity<Void> archive(CurrentUser user, @PathVariable long id) {
        children.archive(user, id);
        return ResponseEntity.noContent().build();
    }
}
