package io.forest.composableretrieval.adapters;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Shared tokenization utility for ColBERT-inspired token units.
 */
public final class ColbertTokenizationUtils {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-z0-9]+");

    private ColbertTokenizationUtils() {
    }

    public static List<String> buildTokenUnits(String text) {
        String normalized = text == null ? "" : text.toLowerCase();
        String[] rawTokens = TOKEN_SPLIT.split(normalized);

        List<String> tokens = new ArrayList<>();
        for (String token : rawTokens) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }

        Set<String> units = new LinkedHashSet<>(tokens);
        for (int i = 0; i < tokens.size() - 1; i++) {
            units.add(tokens.get(i) + " " + tokens.get(i + 1));
        }

        return new ArrayList<>(units);
    }
}
