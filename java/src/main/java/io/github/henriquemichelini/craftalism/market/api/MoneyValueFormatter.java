package io.github.henriquemichelini.craftalism.market.api;

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyValueFormatter {
    private static final int SCALE = 4;

    private MoneyValueFormatter() {}

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        if (value.matches("-?\\d+")) {
            return fixedPoint(new BigDecimal(value));
        }

        return value;
    }

    public static String normalize(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "Unavailable";
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            BigDecimal value = element.getAsBigDecimal();
            if (value.scale() <= 0) {
                return fixedPoint(value);
            }

            return value.stripTrailingZeros().toPlainString();
        }

        return normalize(element.getAsString());
    }

    private static String fixedPoint(BigDecimal value) {
        return value
            .movePointLeft(SCALE)
            .setScale(SCALE, RoundingMode.UNNECESSARY)
            .toPlainString();
    }
}
