package com.kidsvocab.core.game;

import com.kidsvocab.core.game.WordSearch.Direction;
import com.kidsvocab.core.game.WordSearch.Grid;
import com.kidsvocab.core.game.WordSearch.Layout;
import com.kidsvocab.core.game.WordSearch.Placement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WordSearchTest {

    private static final String EN = "abcdefghijklmnopqrstuvwxyz";
    private static final List<String> ANIMALS = List.of("dog", "cat", "horse", "teddy bear", "T-shirt", "x", "elephant");

    @Test
    void lettersDropEverythingButLetters() {
        assertThat(WordSearch.letters("Teddy bear")).isEqualTo("teddybear");
        assertThat(WordSearch.letters("T-shirt")).isEqualTo("tshirt");
        assertThat(WordSearch.letters("it's")).isEqualTo("its");
        assertThat(WordSearch.letters("Straße")).isEqualTo("straße");
    }

    @Test
    void everyUsableWordIsHiddenWhereThePlacementSays() {
        Layout layout = WordSearch.layout(ANIMALS, true, 42);
        Grid grid = layout.fill("", EN);

        assertThat(grid.size()).isBetween(WordSearch.MIN_SIZE, WordSearch.MAX_SIZE);
        assertThat(grid.rows()).hasSize(grid.size()).allSatisfy(r -> assertThat(r).hasSize(grid.size()).matches("[a-z]+"));
        assertThat(grid.placements()).extracting(Placement::word).as("one-letter word left out")
                .containsExactly(0, 1, 2, 3, 4, 6);
        for (Placement p : grid.placements()) {
            assertThat(read(grid, p)).isEqualTo(WordSearch.letters(ANIMALS.get(p.word())));
        }
    }

    @Test
    void theSameSeedGivesTheSameGrid() {
        Grid a = WordSearch.layout(ANIMALS, true, 7).fill("", EN);
        Grid b = WordSearch.layout(ANIMALS, true, 7).fill("", EN);
        Grid c = WordSearch.layout(ANIMALS, true, 8).fill("", EN);
        assertThat(a).isEqualTo(b);
        assertThat(c).isNotEqualTo(a);
    }

    @Test
    void diagonalsOnlyWhenAllowed() {
        for (long seed = 0; seed < 30; seed++) {
            assertThat(WordSearch.layout(ANIMALS, false, seed).placements())
                    .extracting(Placement::direction).doesNotContain(Direction.DOWN_RIGHT);
        }
        boolean diagonalSeen = false;
        for (long seed = 0; seed < 30 && !diagonalSeen; seed++) {
            diagonalSeen = WordSearch.layout(ANIMALS, true, seed).placements().stream()
                    .anyMatch(p -> p.direction() == Direction.DOWN_RIGHT);
        }
        assertThat(diagonalSeen).isTrue();
    }

    @Test
    void gridSizeFollowsTheWords() {
        assertThat(WordSearch.layout(List.of(), false, 1).size()).isEqualTo(WordSearch.MIN_SIZE);
        assertThat(WordSearch.layout(List.of("cat", "dog"), false, 1).size()).isEqualTo(WordSearch.MIN_SIZE);
        assertThat(WordSearch.layout(List.of("caterpillar"), false, 1).placements()).as("too long").isEmpty();
        assertThat(WordSearch.layout(List.of("strawberry"), false, 1).size()).as("longest word").isEqualTo(10);
        List<String> many = List.of("apple", "banana", "cherry", "grapes", "lemon", "orange");
        assertThat(WordSearch.layout(many, false, 1).size()).as("about twice the letters").isEqualTo(9);
        List<String> lots = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lots.add("abcdefgh");
        }
        assertThat(WordSearch.layout(lots, false, 1).size()).as("capped").isEqualTo(WordSearch.MAX_SIZE);
    }

    @Test
    void wordsWithoutRoomAreLeftOut() {
        List<String> lines = new ArrayList<>();
        for (char c = 'a'; c <= 'k'; c++) {
            lines.add(String.valueOf(c).repeat(10));
        }
        Layout layout = WordSearch.layout(lines, false, 3);
        assertThat(layout.placements()).hasSizeLessThan(lines.size()).isNotEmpty();
        assertThat(layout.freeCells()).isEqualTo(100 - 10 * layout.placements().size());
    }

    @Test
    void fillerLettersOfTheAlphabetGoFirstThenRandomOnes() {
        Layout layout = WordSearch.layout(List.of("cat"), false, 5);
        int free = layout.freeCells();
        assertThat(free).isEqualTo(36 - 3);
        Grid grid = layout.fill("Q1 q-QQ", EN);
        String all = String.join("", grid.rows());
        long qs = all.chars().filter(ch -> ch == 'q').count();
        assertThat(qs).as("the four q from the filler, maybe more at random").isGreaterThanOrEqualTo(4);
        assertThat(all).matches("[a-z]+");
        // the first free cells, row by row, carry the filler
        StringBuilder firstFree = new StringBuilder();
        for (int i = 0; i < all.length() && firstFree.length() < 4; i++) {
            if (!isPlaced(grid, i / grid.size(), i % grid.size())) {
                firstFree.append(all.charAt(i));
            }
        }
        assertThat(firstFree.toString()).isEqualTo("qqqq");
    }

    @Test
    void hiddenWordsAppearOnlyWherePlaced() {
        Layout layout = WordSearch.layout(List.of("cat", "tac"), false, 11);
        Grid grid = layout.fill("cat".repeat(40), "act");
        int found = 0;
        for (String word : List.of("cat", "tac")) {
            found += occurrences(grid, word);
        }
        assertThat(found).as("cat and its mirror tac, once each, read both ways").isEqualTo(4);
    }

    @Test
    void alphabetNeedsTwoLetters() {
        Layout layout = WordSearch.layout(List.of("aa"), false, 1);
        assertThatThrownBy(() -> layout.fill("", "a")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void spotsMadeOfPlacedLettersOnlyStay() {
        // "ab" placed at row 0; row 1 also reads "ab" but its cells count as placed (fixed)
        for (long seed = 0; seed < 10; seed++) {
            char[][] grid = {"ab".toCharArray(), "ab".toCharArray()};
            boolean[][] fixed = {{true, true}, {true, true}};
            WordSearch.removeExtraAnswers(grid, fixed, List.of("ab"),
                    List.of(new Placement(0, 0, 0, Direction.RIGHT)), List.of(Direction.RIGHT), "ab",
                    new SplittableRandom(seed));
            assertThat(new String(grid[1])).isEqualTo("ab");
        }
    }

    @Test
    void anUnwantedCopyLosesAFillerLetter() {
        for (long seed = 0; seed < 10; seed++) {
            char[][] grid = {"ab".toCharArray(), "ba".toCharArray()};
            boolean[][] fixed = {{true, true}, {true, false}};
            WordSearch.removeExtraAnswers(grid, fixed, List.of("ab"),
                    List.of(new Placement(0, 0, 0, Direction.RIGHT)), List.of(Direction.RIGHT), "ab",
                    new SplittableRandom(seed));
            assertThat(new String(grid[0])).isEqualTo("ab");
            assertThat(new String(grid[1])).isEqualTo("bb");
        }
    }

    private static String read(Grid grid, Placement p) {
        int length = WordSearch.letters(ANIMALS.get(p.word())).length();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(grid.rows().get(p.row() + i * p.direction().dRow).charAt(p.col() + i * p.direction().dCol));
        }
        return sb.toString();
    }

    private static boolean isPlaced(Grid grid, int row, int col) {
        for (Placement p : grid.placements()) {
            for (int i = 0; i < 3; i++) {
                if (p.row() + i * p.direction().dRow == row && p.col() + i * p.direction().dCol == col) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Readings of the word in rows and columns, forwards and backwards. */
    private static int occurrences(Grid grid, String word) {
        List<String> lines = new ArrayList<>(grid.rows());
        for (int col = 0; col < grid.size(); col++) {
            StringBuilder sb = new StringBuilder();
            for (String row : grid.rows()) {
                sb.append(row.charAt(col));
            }
            lines.add(sb.toString());
        }
        int count = 0;
        for (String line : lines) {
            for (String text : List.of(line, new StringBuilder(line).reverse().toString())) {
                for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
                    count++;
                }
            }
        }
        return count;
    }
}
