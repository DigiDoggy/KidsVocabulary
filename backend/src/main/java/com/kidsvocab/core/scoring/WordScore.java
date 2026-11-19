package com.kidsvocab.core.scoring;

import java.math.BigDecimal;

public record WordScore(BigDecimal points, Outcome outcome) {
}
