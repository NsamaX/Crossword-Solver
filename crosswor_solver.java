import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

class CrosswordSolver {
    private final Map<Integer, List<String>> wordsByLength;
    private final Map<String, String[]> wordCache;

    public CrosswordSolver(String... wordFileNames) {
        this.wordsByLength = new HashMap<>();
        this.wordCache = new HashMap<>();
        for (String fileName : wordFileNames) {
            readWordsFromFile(fileName);
        }
        System.out.println("Loaded " + getTotalWordCount() + " words from " + wordFileNames.length + " file(s)");
    }

    private void readWordsFromFile(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName, StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    String word = trimmed.toUpperCase();
                    int length = word.length();
                    wordsByLength.computeIfAbsent(length, k -> new ArrayList<>()).add(word);
                    count++;
                }
            }
            System.out.println("Loaded " + count + " words from: " + fileName);
        } catch (IOException e) {
            System.err.println("Error reading file " + fileName + ": " + e.getMessage());
        }
    }
    
    private int getTotalWordCount() {
        return wordsByLength.values().stream()
            .mapToInt(List::size)
            .sum();
    }

    private char[][] convertCrossword(String[] crossword) {
        char[][] grid = new char[crossword.length][crossword[0].length()];
        for (int i = 0; i < crossword.length; i++) {
            grid[i] = crossword[i].toUpperCase().toCharArray();
        }
        return grid;
    }

    private String crosswordToString(char[][] crossword) {
        StringBuilder sb = new StringBuilder();
        for (char[] row : crossword) {
            for (char c : row) {
                if (c != '#') {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private String[] getPossibleWords(int targetLength, String alphabet) {
        String cacheKey = targetLength + ":" + alphabet;
        if (wordCache.containsKey(cacheKey)) {
            return wordCache.get(cacheKey);
        }
        ArrayList<String> possibleWords = new ArrayList<>();
        List<String> wordsOfLength = wordsByLength.get(targetLength);
        if (wordsOfLength == null) {
            return new String[0];
        }
        int[] charCount = new int[26];
        for (char c : alphabet.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                charCount[c - 'A']++;
            }
        }
        for (String word : wordsOfLength) {
            int[] tempCharCount = charCount.clone();
            boolean canForm = true;
            for (char c : word.toCharArray()) {
                if (c < 'A' || c > 'Z' || tempCharCount[c - 'A'] == 0) {
                    canForm = false;
                    break;
                }
                tempCharCount[c - 'A']--;
            }
            if (canForm) {
                possibleWords.add(word);
            }
        }
        String[] result = possibleWords.toArray(new String[0]);
        wordCache.put(cacheKey, result);
        return result;
    }

    private Node[][] createNodeGrid(char[][] crossword) {
        int rows = crossword.length;
        int cols = crossword[0].length;
        Node[][] nodeGrid = new Node[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                nodeGrid[i][j] = new Node(i, j, crossword[i][j]);
            }
        }
        int[] dr = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dc = {-1, 0, 1, -1, 1, -1, 0, 1};
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (crossword[i][j] == '#') continue;
                for (int d = 0; d < 8; d++) {
                    int ni = i + dr[d];
                    int nj = j + dc[d];
                    if (ni >= 0 && ni < rows && nj >= 0 && nj < cols && crossword[ni][nj] != '#') {
                        nodeGrid[i][j].addConnection(nodeGrid[ni][nj]);
                    }
                }
            }
        }
        return nodeGrid;
    }

    private char[][] applyGravity(char[][] crossword, int[][] usedPositions) {
        int rows = crossword.length;
        int cols = crossword[0].length;
        char[][] newGrid = new char[rows][cols];
        for (int i = 0; i < rows; i++) {
            System.arraycopy(crossword[i], 0, newGrid[i], 0, cols);
        }
        for (int[] pos : usedPositions) {
            int row = pos[0];
            int col = pos[1];
            if (row >= 0 && row < rows && col >= 0 && col < cols) {
                newGrid[row][col] = '$';
            }
        }
        for (int j = 0; j < cols; j++) {
            int writePos = rows - 1;
            for (int i = rows - 1; i >= 0; i--) {
                if (newGrid[i][j] != '$' && newGrid[i][j] != '#') {
                    newGrid[writePos][j] = newGrid[i][j];
                    if (writePos != i) {
                        newGrid[i][j] = '#';
                    }
                    writePos--;
                }
            }
            for (int i = writePos; i >= 0; i--) {
                if (newGrid[i][j] != '#') {
                    newGrid[i][j] = '#';
                }
            }
        }
        return newGrid;
    }

    private boolean isConnectedPath(ArrayList<Node> nodes) {
        if (nodes.size() <= 1) return true;
        for (int i = 0; i < nodes.size() - 1; i++) {
            Node current = nodes.get(i);
            Node next = nodes.get(i + 1);
            boolean isConnected = false;
            for (Node conn : current.getConnections()) {
                if (conn == next) {
                    isConnected = true;
                    break;
                }
            }
            if (!isConnected) {
                return false;
            }
        }
        return true;
    }

    private ArrayList<Node> findNodesForLetter(Node[][] nodeGrid, char letter) {
        ArrayList<Node> nodes = new ArrayList<>();
        for (Node[] row : nodeGrid) {
            for (Node node : row) {
                if (node.getLetter() == letter && node.getLetter() != '#') {
                    nodes.add(node);
                }
            }
        }
        return nodes;
    }

    private ArrayList<Path> findAllValidPathsForWord(String word, Node[][] nodeGrid) {
        ArrayList<Path> validPaths = new ArrayList<>();
        ArrayList<Node> currentPath = new ArrayList<>();
        boolean[] used = new boolean[nodeGrid.length * nodeGrid[0].length];

        class PathFinder {
            void findPath(int index) {
                if (index == word.length()) {
                    if (isConnectedPath(currentPath)) {
                        validPaths.add(new Path(word, currentPath.toArray(new Node[0])));
                    }
                    return;
                }
                char letter = word.charAt(index);
                ArrayList<Node> candidates = findNodesForLetter(nodeGrid, letter);
                for (Node candidate : candidates) {
                    int posIndex = candidate.getRow() * nodeGrid[0].length + candidate.getCol();
                    if (!used[posIndex]) {
                        if (index == 0 || (!currentPath.isEmpty() && currentPath.get(currentPath.size() - 1).getConnections().contains(candidate))) {
                            currentPath.add(candidate);
                            used[posIndex] = true;
                            findPath(index + 1);
                            currentPath.remove(currentPath.size() - 1);
                            used[posIndex] = false;
                        }
                    }
                }
            }
        }

        new PathFinder().findPath(0);
        return validPaths;
    }

    private ArrayList<Path> findAllConnectedWords(String[] possibleWords, int targetLength, Node[][] nodeGrid) {
        ArrayList<Path> allValidPaths = new ArrayList<>();
        for (String word : possibleWords) {
            if (word.length() == targetLength) {
                allValidPaths.addAll(findAllValidPathsForWord(word, nodeGrid));
            }
        }
        return allValidPaths;
    }

    private void solveCrossword(int index, char[][] currentCrossword, int[] lengths, ArrayList<Path> currentSolution, List<List<Path>> allSolutions, Set<String> wordSetSignatures) {
        if (index == lengths.length) {
            String signature = currentSolution.stream()
                .map(Path::getWord)
                .sorted()
                .collect(Collectors.joining(","));
            if (!wordSetSignatures.contains(signature)) {
                wordSetSignatures.add(signature);
                allSolutions.add(new ArrayList<>(currentSolution));
            }
            return;
        }
        String alphabet = crosswordToString(currentCrossword);
        String[] possibleWords = getPossibleWords(lengths[index], alphabet);
        Node[][] nodeGrid = createNodeGrid(currentCrossword);
        ArrayList<Path> validPaths = findAllConnectedWords(possibleWords, lengths[index], nodeGrid);
        for (Path path : validPaths) {
            if (path.isValid()) {
                int[][] usedPositions = new int[path.getPositions().size()][2];
                for (int j = 0; j < path.getPositions().size(); j++) {
                    Node node = path.getPositions().get(j);
                    usedPositions[j][0] = node.getRow();
                    usedPositions[j][1] = node.getCol();
                }
                char[][] nextCrossword = applyGravity(currentCrossword, usedPositions);
                currentSolution.add(path);
                solveCrossword(index + 1, nextCrossword, lengths, currentSolution, allSolutions, wordSetSignatures);
                currentSolution.remove(currentSolution.size() - 1);
            }
        }
    }

    public void solve(String[] crosswordInput, int[] targetLengths) {
        try {
            final char[][] crosswordGrid = convertCrossword(crosswordInput);
            System.out.println("Grid size: " + crosswordGrid.length + "x" + crosswordGrid[0].length);
            System.out.println("Target word lengths: " + java.util.Arrays.toString(targetLengths));
            long startTime = System.currentTimeMillis();
            List<List<Path>> allSolutions = new ArrayList<>();
            Set<String> wordSetSignatures = new HashSet<>();
            solveCrossword(0, crosswordGrid, targetLengths, new ArrayList<>(), allSolutions, wordSetSignatures);
            long endTime = System.currentTimeMillis();
            double computationTime = (endTime - startTime) / 1000.0;
            System.out.printf("\nComputation time: %.3f seconds\n\n", computationTime);
            if (allSolutions.isEmpty()) {
                System.out.println("No possible solutions found.");
            } else {
                System.out.println("Possible " + allSolutions.size() + " solution(s)");
                try (BufferedWriter writer = new BufferedWriter(new FileWriter("path.txt", StandardCharsets.UTF_8))) {
                    writer.write("Possible " + allSolutions.size() + " solution(s)\n");
                    for (int s = 0; s < allSolutions.size(); s++) {
                        List<Path> solution = allSolutions.get(s);
                        String solutionWords = solution.stream()
                            .map(Path::getWord)
                            .collect(Collectors.joining(" "));
                        System.out.println((s + 1) + ". " + solutionWords);
                        writer.write("\nSolution " + (s + 1) + "\n");
                        for (Path path : solution) {
                            writer.write("     " + path.getWord() + "\n");
                            writer.write("     ");
                            List<Node> positions = path.getPositions();
                            for (int i = 0; i < positions.size(); i++) {
                                Node node = positions.get(i);
                                writer.write("[" + (node.getRow() + 1) + "," + (node.getCol() + 1) + "]");
                                if (i < positions.size() - 1) {
                                    writer.write(" ");
                                }
                            }
                            writer.write("\n");
                        }
                    }
                    System.out.println("\nResults written to path.txt");
                } catch (IOException e) {
                    System.err.println("Error writing to path.txt: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error solving crossword: " + e.getMessage());
            e.printStackTrace();
        }
    }
}