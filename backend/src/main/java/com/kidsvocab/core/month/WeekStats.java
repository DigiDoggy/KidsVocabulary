package com.kidsvocab.core.month;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WeekStats(int index, LocalDate startDate, BigDecimal points, BigDecimal maxPoints,
                        BigDecimal percent, int learned, int hard, int unlearned) {
}
