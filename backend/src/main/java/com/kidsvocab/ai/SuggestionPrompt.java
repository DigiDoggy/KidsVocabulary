package com.kidsvocab.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The instructions sent to every model, so all providers ask the same question. */
final class SuggestionPrompt {

    /** Upper bound for the exclusion list, to keep the prompt small. */
    static final int MAX_EXCLUDED = 300;

    static final Map<String, String> LANGUAGE_NAMES = Map.of(
            "de", "German", "en", "English", "ru", "Russian", "et", "Estonian");

    /**
     * The answer's shape for APIs with structured output. Optional fields are asked for only when they carry
     * information: small models tend to ramble inside a redundant field (a topic the request already names,
     * an English meaning equal to the English target) until the answer runs out of tokens.
     */
    static Map<String, Object> schema(SuggestionRequest r) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("source", Map.of("type", "STRING"));
        properties.put("target", Map.of("type", "STRING"));
        if (asksTopic(r)) {
            properties.put("topic", Map.of("type", "STRING"));
        }
        if (asksEnglish(r)) {
            properties.put("english", Map.of("type", "STRING"));
        }
        properties.put("emoji", Map.of("type", "STRING"));
        return Map.of(
                "type", "OBJECT",
                "properties", Map.of("words", Map.of(
                        "type", "ARRAY",
                        "items", Map.of(
                                "type", "OBJECT",
                                "properties", properties,
                                "required", List.of("source", "target")))),
                "required", List.of("words"));
    }

    /** The topic is asked for only when the model picks it from a list. */
    static boolean asksTopic(SuggestionRequest r) {
        return r.topic() == null;
    }

    /** For an English target the English meaning is the target itself. */
    static boolean asksEnglish(SuggestionRequest r) {
        return !"en".equals(r.targetLang());
    }

    private SuggestionPrompt() {
    }

    static String system(SuggestionRequest r) {
        boolean topic = asksTopic(r);
        boolean english = asksEnglish(r);
        String shape = "{\"source\": \"...\", \"target\": \"...\""
                + (topic ? ", \"topic\": \"...\"" : "")
                + (english ? ", \"english\": \"...\"" : "")
                + ", \"emoji\": \"...\"}";
        StringBuilder sb = new StringBuilder()
                .append("You suggest vocabulary for children who learn to spell words in a foreign language.\n")
                .append("Reply with JSON only, exactly in this shape:\n")
                .append("{\"words\": [").append(shape).append("]}\n")
                .append("Rules:\n")
                .append("- \"source\" is the word in the child's native language, \"target\" is its translation.\n");
        if (topic) {
            sb.append("- \"topic\" is one of the topics to choose from.\n");
        }
        if (english) {
            sb.append("- \"english\" is the English word for the same meaning.\n");
        }
        return sb.append("""
                - "emoji" is one emoji that a child would clearly recognise as this word (an apple for "apple");
                  an empty string when no emoji shows the meaning clearly.
                - Every value is just the word itself: no explanations, notes or comments inside a value.
                - Common, concrete, child-friendly everyday words; nouns in the singular, verbs in the infinitive.
                - One word or a two-word phrase, no articles, no punctuation.
                - Lower case, except where the language's normal spelling needs a capital letter
                  (German nouns start with one: "Hund", "Apfel").
                - Use the standard spelling with all its letters (ä, ö, ü, ß, õ, š ...), never substitutes.
                - Each source word has one clear translation, and every target word appears only once.
                - Nothing about violence, weapons, alcohol, drugs, adult topics, brands or people's names.
                """).toString();
    }

    static String user(SuggestionRequest r) {
        StringBuilder sb = new StringBuilder()
                .append("Native language: ").append(name(r.nativeLang())).append('\n')
                .append("Target language: ").append(name(r.targetLang())).append('\n')
                .append("Child: ").append(r.childAge() == null ? "school age" : r.childAge() + " years old")
                .append(", level ").append(r.level()).append(" (A0 = beginner)\n")
                .append("Number of words: ").append(r.count()).append('\n');
        if (r.topic() != null) {
            sb.append("Topic: ").append(r.topic()).append('\n');
        } else if (!r.topics().isEmpty()) {
            sb.append("Topics to choose from: ").append(String.join(", ", r.topics())).append('\n');
        }
        if (!r.excludeTargetWords().isEmpty()) {
            sb.append("Do not suggest these ").append(name(r.targetLang())).append(" words: ")
                    .append(r.excludeTargetWords().stream().sorted().limit(MAX_EXCLUDED)
                            .collect(Collectors.joining(", ")))
                    .append('\n');
        }
        return sb.toString();
    }

    static String name(String lang) {
        return LANGUAGE_NAMES.getOrDefault(lang, lang);
    }
}
