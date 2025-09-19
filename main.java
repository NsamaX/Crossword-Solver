import java.io.BufferedReader;
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

class Node {
    private final int row;
    private final int col;
    private final char letter;
    private final ArrayList<Node> connections;

    public Node(int row, int col, char letter) {
        this.row = row;
        this.col = col;
        this.letter = letter;
        this.connections = new ArrayList<>();
    }

    public void addConnection(Node node) {
        connections.add(node);
    }

    public int getRow() {
        return row;
    }

    public int getCol() {
        return col;
    }

    public char getLetter() {
        return letter;
    }

    public ArrayList<Node> getConnections() {
        return connections;
    }
}

class Path {
    private final String word;
    private final ArrayList<Node> positions;

    public Path(String word, Node[] nodes) {
        this.word = word;
        this.positions = new ArrayList<>();
        for (Node node : nodes) {
            this.positions.add(node);
        }
    }

    public String getWord() {
        return word;
    }

    public ArrayList<Node> getPositions() {
        return positions;
    }

    public boolean isValid() {
        return positions.size() == word.length();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("word: ").append(word).append("\n\n");
        for (int i = 0; i < positions.size(); i++) {
            Node node = positions.get(i);
            sb.append("Letter ").append(word.charAt(i))
              .append(" at position (").append(node.getRow() + 1).append(",").append(node.getCol() + 1).append(")\n");
        }
        return sb.toString();
    }
}

class CrosswordSolver {
    private final String[] words;
    private final Map<String, String[]> wordCache;

    public CrosswordSolver(String wordFileName) {
        this.words = readWordsFromFile(wordFileName);
        this.wordCache = new HashMap<>();
    }

    private String[] readWordsFromFile(String fileName) {
        ArrayList<String> wordList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(fileName, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    wordList.add(trimmed.toUpperCase());
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            return new String[0];
        }
        return wordList.toArray(new String[0]);
    }

    private char[][] convertCrossword(String[] crossword) {
        if (crossword == null || crossword.length == 0) {
            throw new IllegalArgumentException("Crossword grid cannot be empty");
        }
        int rows = crossword.length;
        int cols = crossword[0].length();
        if (rows < 2 || cols < 2) {
            throw new IllegalArgumentException("Crossword grid must be at least 2x2");
        }
        for (String row : crossword) {
            if (row.length() != cols) {
                throw new IllegalArgumentException("All rows must have the same length");
            }
        }
        char[][] grid = new char[rows][cols];
        for (int i = 0; i < rows; i++) {
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
        int[] charCount = new int[26];
        for (char c : alphabet.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                charCount[c - 'A']++;
            }
        }
        for (String word : words) {
            if (word.length() != targetLength) {
                continue;
            }
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

    public void solve(String[] crosswordInput, int[] targetLengths, String outputFileName) {
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
            System.out.printf("Computation time: %.3f seconds\n\n", computationTime);
            StringBuilder output = new StringBuilder();
            if (allSolutions.isEmpty()) {
                String message = "No complete solutions found.";
                System.out.println(message);
                output.append(message).append("\n");
            } else {
                String message = "Found " + allSolutions.size() + " complete solutions.\n";
                System.out.println(message);
                output.append(message).append("\n");
                
                for (int s = 0; s < allSolutions.size(); s++) {
                    List<Path> solution = allSolutions.get(s);
                    String solutionHeader = "Solution " + (s + 1) + ": " + solution.stream()
                        .map(Path::getWord)
                        .collect(Collectors.joining(" "));
                    System.out.println(solutionHeader);
                    output.append(solutionHeader).append("\n");
                    
                    char[][] tempCrossword = new char[crosswordGrid.length][crosswordGrid[0].length];
                    for (int i = 0; i < crosswordGrid.length; i++) {
                        System.arraycopy(crosswordGrid[i], 0, tempCrossword[i], 0, crosswordGrid[0].length);
                    }
                    for (Path path : solution) {
                        output.append("\n").append(path.toString());
                        int[][] usedPositions = new int[path.getPositions().size()][2];
                        for (int j = 0; j < path.getPositions().size(); j++) {
                            Node node = path.getPositions().get(j);
                            usedPositions[j][0] = node.getRow();
                            usedPositions[j][1] = node.getCol();
                        }
                        tempCrossword = applyGravity(tempCrossword, usedPositions);
                    }
                    output.append("\n");
                }
            }
            try (FileWriter writer = new FileWriter(outputFileName, StandardCharsets.UTF_8)) {
                writer.write(output.toString());
            }
        } catch (Exception e) {
            System.err.println("Error solving crossword: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

class Main {
    public static void main(String[] args) {        
        CrosswordSolver crosswordSolver = new CrosswordSolver("languages/english.txt");
        
        String[] crossword = {
            "ELQRD",
            "CYUEA",
            "TNPSO",
            "IACRR",
            "YTMIR"
        };
        
        int[] lengths = {6, 8, 6, 5};
        
        crosswordSolver.solve(crossword, lengths, "answers.txt");
    }
}
