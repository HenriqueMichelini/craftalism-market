package io.github.henriquemichelini.craftalism.market.api;

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyValueFormatter {
    private static final int MAX_DISPLAY_SCALE = 4;

    private MoneyValueFormatter() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        try {
            BigDecimal parsed = new BigDecimal(value);
            return formatDisplayValue(parsed);
        } catch (NumberFormatException exception) {
            return value;
        }
    }

    public static String normalize(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "Unavailable";
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return formatDisplayValue(element.getAsBigDecimal());
        }

        return normalize(element.getAsString());
    }

    private static String formatDisplayValue(BigDecimal value) {
        return value
            .setScale(MAX_DISPLAY_SCALE, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString();
    }
}
