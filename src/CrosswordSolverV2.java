package src;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

class CrosswordSolverV2 {

    private static class TrieNode {
        final TrieNode[] children = new TrieNode[26];
        String word = null;
    }

    private static final int[] DR = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int[] DC = {-1, 0, 1, -1, 1, -1, 0, 1};

    private final TrieNode trieRoot = new TrieNode();

    public CrosswordSolverV2(String... wordFileNames) {
        int total = 0;
        for (String fileName : wordFileNames) {
            total += loadWords(fileName);
        }
        System.out.println("Loaded " + total + " words from " + wordFileNames.length + " file(s)");
    }

    private int loadWords(String fileName) {
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(fileName, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String word = line.trim().toUpperCase();
                if (!word.isEmpty() && word.chars().allMatch(c -> c >= 'A' && c <= 'Z')) {
                    insertTrie(word);
                    count++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading " + fileName + ": " + e.getMessage());
        }
        System.out.println("Loaded " + count + " words from: " + fileName);
        return count;
    }

    private void insertTrie(String word) {
        TrieNode node = trieRoot;
        for (char c : word.toCharArray()) {
            int i = c - 'A';
            if (node.children[i] == null) node.children[i] = new TrieNode();
            node = node.children[i];
        }
        node.word = word;
    }

    private char[][] toGrid(String[] rows) {
        char[][] grid = new char[rows.length][rows[0].length()];
        for (int i = 0; i < rows.length; i++) grid[i] = rows[i].toUpperCase().toCharArray();
        return grid;
    }

    private char[][] applyGravity(char[][] grid, boolean[][] used) {
        int rows = grid.length, cols = grid[0].length;
        char[][] next = new char[rows][cols];
        for (char[] row : next) Arrays.fill(row, '#');
        for (int j = 0; j < cols; j++) {
            int writePos = rows - 1;
            for (int i = rows - 1; i >= 0; i--) {
                if (grid[i][j] != '#' && !used[i][j]) {
                    next[writePos--][j] = grid[i][j];
                }
            }
        }
        return next;
    }

    // Trie-guided DFS: only explores branches that exist in the trie
    private List<Path> findPaths(char[][] grid, int targetLength) {
        int rows = grid.length, cols = grid[0].length;
        List<Path> results = new ArrayList<>();
        boolean[][] visited = new boolean[rows][cols];
        int[] pathRows = new int[targetLength];
        int[] pathCols = new int[targetLength];

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == '#') continue;
                TrieNode child = trieRoot.children[grid[i][j] - 'A'];
                if (child == null) continue;
                visited[i][j] = true;
                pathRows[0] = i;
                pathCols[0] = j;
                dfs(grid, i, j, child, targetLength, 1, visited, pathRows, pathCols, results);
                visited[i][j] = false;
            }
        }
        return results;
    }

    private void dfs(char[][] grid, int row, int col, TrieNode node, int targetLength,
                     int depth, boolean[][] visited, int[] pathRows, int[] pathCols, List<Path> results) {
        if (depth == targetLength) {
            if (node.word != null) {
                Node[] nodes = new Node[depth];
                for (int k = 0; k < depth; k++) {
                    nodes[k] = new Node(pathRows[k], pathCols[k], grid[pathRows[k]][pathCols[k]]);
                }
                results.add(new Path(node.word, nodes));
            }
            return;
        }
        int rows = grid.length, cols = grid[0].length;
        for (int d = 0; d < 8; d++) {
            int ni = row + DR[d], nj = col + DC[d];
            if (ni < 0 || ni >= rows || nj < 0 || nj >= cols) continue;
            if (grid[ni][nj] == '#' || visited[ni][nj]) continue;
            TrieNode child = node.children[grid[ni][nj] - 'A'];
            if (child == null) continue;
            visited[ni][nj] = true;
            pathRows[depth] = ni;
            pathCols[depth] = nj;
            dfs(grid, ni, nj, child, targetLength, depth + 1, visited, pathRows, pathCols, results);
            visited[ni][nj] = false;
        }
    }

    private void backtrack(int step, int[] orderedLengths, int[] originalIndices,
                           char[][] grid, Path[] currentSolution,
                           List<Path[]> allSolutions, Set<String> seen) {
        if (step == orderedLengths.length) {
            Path[] reordered = new Path[currentSolution.length];
            for (int i = 0; i < currentSolution.length; i++) {
                reordered[originalIndices[i]] = currentSolution[i];
            }
            String sig = Arrays.stream(reordered).map(Path::getWord).sorted().collect(Collectors.joining(","));
            if (seen.add(sig)) allSolutions.add(reordered.clone());
            return;
        }

        List<Path> candidates = findPaths(grid, orderedLengths[step]);
        for (Path path : candidates) {
            boolean[][] used = new boolean[grid.length][grid[0].length];
            for (Node n : path.getPositions()) used[n.getRow()][n.getCol()] = true;
            currentSolution[step] = path;
            backtrack(step + 1, orderedLengths, originalIndices, applyGravity(grid, used), currentSolution, allSolutions, seen);
        }
    }

    public void solve(String[] crosswordInput, int[] targetLengths) {
        char[][] grid = toGrid(crosswordInput);
        System.out.println("Grid size: " + grid.length + "x" + grid[0].length);
        System.out.println("Target word lengths: " + Arrays.toString(targetLengths));

        // MRV: count candidates on initial grid, solve most constrained first
        int n = targetLengths.length;
        int[] candidateCounts = new int[n];
        for (int i = 0; i < n; i++) {
            candidateCounts[i] = findPaths(grid, targetLengths[i]).size();
            System.out.println("  Length " + targetLengths[i] + ": " + candidateCounts[i] + " candidate(s)");
        }

        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i;
        Arrays.sort(order, Comparator.comparingInt(i -> candidateCounts[i]));

        int[] orderedLengths = new int[n];
        int[] originalIndices = new int[n];
        for (int i = 0; i < n; i++) {
            orderedLengths[i] = targetLengths[order[i]];
            originalIndices[i] = order[i];
        }

        long start = System.currentTimeMillis();
        List<Path[]> allSolutions = new ArrayList<>();
        backtrack(0, orderedLengths, originalIndices, grid, new Path[n], allSolutions, new HashSet<>());
        double elapsed = (System.currentTimeMillis() - start) / 1000.0;

        System.out.printf("\nComputation time: %.3f seconds\n\n", elapsed);

        if (allSolutions.isEmpty()) {
            System.out.println("No possible solutions found.");
            return;
        }

        System.out.println("========================================");
        System.out.println("CROSSWORD SOLVER RESULTS");
        System.out.println("========================================");
        System.out.println("Possible " + allSolutions.size() + " solution(s):");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter("Results.txt", StandardCharsets.UTF_8))) {
            writer.write("Possible " + allSolutions.size() + " solution(s)\n");
            for (int s = 0; s < allSolutions.size(); s++) {
                Path[] solution = allSolutions.get(s);
                String words = Arrays.stream(solution).map(Path::getWord).collect(Collectors.joining(" "));
                System.out.println((s + 1) + ". " + words);
                writer.write("\nSolution " + (s + 1) + "\n");
                for (Path path : solution) {
                    writer.write("     " + path.getWord() + "\n     ");
                    List<Node> positions = path.getPositions();
                    for (int i = 0; i < positions.size(); i++) {
                        Node node = positions.get(i);
                        writer.write("[" + (node.getRow() + 1) + "," + (node.getCol() + 1) + "]");
                        if (i < positions.size() - 1) writer.write(" ");
                    }
                    writer.write("\n");
                }
            }
            System.out.println("\nResults written to Results.txt");
        } catch (IOException e) {
            System.err.println("Error writing results: " + e.getMessage());
        }
    }
}
