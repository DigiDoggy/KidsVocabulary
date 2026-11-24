package com.kidsvocab.core.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

/**
 * The word-search grid ("filword"): words hidden left to right, top to bottom and, if allowed, diagonally
 * down to the right; the free cells are filled with letters. The same words and seed always give the same
 * grid.
 *
 * <p>Built in two steps, so that the caller can ask for exactly as many filler letters as there are free
 * cells: {@link #layout} places the words, {@link Layout#fill} fills the rest. Filling makes sure no hidden
 * word can be read anywhere else in the grid (forwards or backwards).
 */
public final class WordSearch {

    public static final int MIN_SIZE = 6;
    public static final int MAX_SIZE = 10;
    /** Shorter words are not hidden: a single letter is no puzzle. */
    public static final int MIN_LETTERS = 2;

    public enum Direction {
        RIGHT(0, 1), DOWN(1, 0), DOWN_RIGHT(1, 1);

        final int dRow;
        final int dCol;

        Direction(int dRow, int dCol) {
            this.dRow = dRow;
            this.dCol = dCol;
        }
    }

    /** Where a word is hidden. {@code word} is its index in the list given to {@link #layout}. */
    public record Placement(int word, int row, int col, Direction direction) {
    }

    /** The finished grid: {@code size} rows of {@code size} lower-case letters. */
    public record Grid(int size, List<String> rows, List<Placement> placements) {
    }

    private WordSearch() {
    }

    /** The letters of a word as they go into the grid: lower case, without spaces, hyphens or apostrophes. */
    public static String letters(String word) {
        StringBuilder sb = new StringBuilder();
        word.toLowerCase(Locale.ROOT).chars().filter(Character::isLetter).forEach(c -> sb.append((char) c));
        return sb.toString();
    }

    /**
     * Places the words, longest first. The grid is square: big enough for the longest word and roughly
     * twice as many cells as letters, between {@value #MIN_SIZE} and {@value #MAX_SIZE}. Words that are too
     * short, too long or find no room are left out.
     */
    public static Layout layout(List<String> words, boolean diagonal, long seed) {
        List<String> keys = words.stream().map(WordSearch::letters).toList();
        List<Integer> usable = IntStream.range(0, keys.size())
                .filter(i -> keys.get(i).length() >= MIN_LETTERS && keys.get(i).length() <= MAX_SIZE)
                .boxed()
                .sorted(Comparator.comparingInt((Integer i) -> -keys.get(i).length()).thenComparing(i -> i))
                .toList();
        int longest = usable.isEmpty() ? 0 : keys.get(usable.getFirst()).length();
        int letters = usable.stream().mapToInt(i -> keys.get(i).length()).sum();
        int size = Math.min(MAX_SIZE, Math.max(MIN_SIZE, Math.max(longest, (int) Math.ceil(Math.sqrt(letters * 2.0)))));
        List<Direction> directions = diagonal
                ? List.of(Direction.values())
                : List.of(Direction.RIGHT, Direction.DOWN);

        SplittableRandom random = new SplittableRandom(seed);
        char[][] cells = new char[size][size];
        List<Placement> placements = new ArrayList<>();
        for (int index : usable) {
            String key = keys.get(index);
            List<Placement> candidates = new ArrayList<>();
            for (Direction d : directions) {
                for (int row = 0; row < size; row++) {
                    for (int col = 0; col < size; col++) {
                        if (fits(cells, key, row, col, d)) {
                            candidates.add(new Placement(index, row, col, d));
                        }
                    }
                }
            }
            if (!candidates.isEmpty()) {
                Placement p = candidates.get(random.nextInt(candidates.size()));
                for (int i = 0; i < key.length(); i++) {
                    cells[p.row() + i * p.direction().dRow][p.col() + i * p.direction().dCol] = key.charAt(i);
                }
                placements.add(p);
            }
        }
        placements.sort(Comparator.comparingInt(Placement::word));
        return new Layout(size, cells, List.copyOf(placements), keys, directions, random);
    }

    private static boolean fits(char[][] cells, String key, int row, int col, Direction d) {
        int size = cells.length;
        int lastRow = row + (key.length() - 1) * d.dRow;
        int lastCol = col + (key.length() - 1) * d.dCol;
        if (lastRow >= size || lastCol >= size) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = cells[row + i * d.dRow][col + i * d.dCol];
            if (c != 0 && c != key.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Words placed, free cells still empty. */
    public static final class Layout {

        private final int size;
        private final char[][] cells;
        private final List<Placement> placements;
        private final List<String> keys;
        private final List<Direction> directions;
        private final SplittableRandom random;

        private Layout(int size, char[][] cells, List<Placement> placements, List<String> keys,
                       List<Direction> directions, SplittableRandom random) {
            this.size = size;
            this.cells = cells;
            this.placements = placements;
            this.keys = keys;
            this.directions = directions;
            this.random = random;
        }

        public int size() {
            return size;
        }

        public List<Placement> placements() {
            return placements;
        }

        public int freeCells() {
            int free = 0;
            for (char[] row : cells) {
                for (char c : row) {
                    free += c == 0 ? 1 : 0;
                }
            }
            return free;
        }

        /**
         * Fills the free cells, row by row, with the filler's letters that belong to the alphabet, then with
         * random letters of the alphabet once the filler runs out. Afterwards, any other spot where a hidden
         * word can be read gets one of its filler letters changed.
         *
         * @param filler   suggested letters (for example from a language model); may be empty
         * @param alphabet the language's lower-case letters, at least two
         */
        public Grid fill(String filler, String alphabet) {
            if (alphabet.length() < 2) {
                throw new IllegalArgumentException("The alphabet needs at least two letters");
            }
            char[][] grid = new char[size][];
            boolean[][] fixed = new boolean[size][size];
            int next = 0;
            String letters = filler.toLowerCase(Locale.ROOT);
            for (int row = 0; row < size; row++) {
                grid[row] = cells[row].clone();
                for (int col = 0; col < size; col++) {
                    if (grid[row][col] != 0) {
                        fixed[row][col] = true;
                        continue;
                    }
                    while (next < letters.length() && alphabet.indexOf(letters.charAt(next)) < 0) {
                        next++;
                    }
                    grid[row][col] = next < letters.length()
                            ? letters.charAt(next++)
                            : alphabet.charAt(random.nextInt(alphabet.length()));
                }
            }
            removeExtraAnswers(grid, fixed, keys, placements, directions, alphabet, random);
            List<String> rows = new ArrayList<>();
            for (char[] row : grid) {
                rows.add(new String(row));
            }
            return new Grid(size, List.copyOf(rows), placements);
        }
    }

    /**
     * Changes filler letters until each hidden word can only be read where it was placed. A spot made of
     * placed letters only cannot be changed and is left alone (two hidden words may happen to form a third).
     */
    static void removeExtraAnswers(char[][] grid, boolean[][] fixed, List<String> keys, List<Placement> placements,
                                   List<Direction> directions, String alphabet, SplittableRandom random) {
        Map<String, Set<Set<Integer>>> own = new HashMap<>();
        for (Placement p : placements) {
            String key = keys.get(p.word());
            own.computeIfAbsent(key, k -> new HashSet<>()).add(cells(grid.length, p.row(), p.col(), p.direction(), key.length()));
        }
        int cell = fixableCell(grid, fixed, own, directions);
        while (cell >= 0) {
            int row = cell / grid.length;
            int col = cell % grid.length;
            int pick = random.nextInt(alphabet.length());
            if (alphabet.charAt(pick) == grid[row][col]) {
                pick = (pick + 1) % alphabet.length();
            }
            grid[row][col] = alphabet.charAt(pick);
            cell = fixableCell(grid, fixed, own, directions);
        }
    }

    /** A filler cell inside an unwanted occurrence of a hidden word, or -1. */
    private static int fixableCell(char[][] grid, boolean[][] fixed, Map<String, Set<Set<Integer>>> own,
                                   List<Direction> directions) {
        int size = grid.length;
        for (Map.Entry<String, Set<Set<Integer>>> entry : own.entrySet()) {
            String key = entry.getKey();
            String reversed = new StringBuilder(key).reverse().toString();
            for (Direction d : directions) {
                for (int row = 0; row < size; row++) {
                    for (int col = 0; col < size; col++) {
                        String text = read(grid, row, col, d, key.length());
                        if (!key.equals(text) && !reversed.equals(text)) {
                            continue;
                        }
                        Set<Integer> spot = cells(size, row, col, d, key.length());
                        if (entry.getValue().contains(spot)) {
                            continue;
                        }
                        for (int c : spot) {
                            if (!fixed[c / size][c % size]) {
                                return c;
                            }
                        }
                    }
                }
            }
        }
        return -1;
    }

    /** The text from a cell in a direction, or {@code null} if it runs off the grid. */
    private static String read(char[][] grid, int row, int col, Direction d, int length) {
        int size = grid.length;
        if (row + (length - 1) * d.dRow >= size || col + (length - 1) * d.dCol >= size) {
            return null;
        }
        char[] text = new char[length];
        for (int i = 0; i < length; i++) {
            text[i] = grid[row + i * d.dRow][col + i * d.dCol];
        }
        return new String(text);
    }

    private static Set<Integer> cells(int size, int row, int col, Direction d, int length) {
        Set<Integer> cells = new HashSet<>();
        for (int i = 0; i < length; i++) {
            cells.add((row + i * d.dRow) * size + col + i * d.dCol);
        }
        return cells;
    }
}
