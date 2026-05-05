package src;

class Main {
    public static void main(String[] args) {
        try {
            String imagePath = ImageFileLocator.findFirstPngInCurrentDir();
            CrosswordReader.Result result = CrosswordReader.readFromImage(imagePath);
            String[] crossword = result.getCrossword();
            int[] lengths = result.getLengths();
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
            // English source: https://github.com/david47k/top-english-wordlists
            CrosswordSolverV2 crosswordSolver = new CrosswordSolverV2(
                "src/languages/english-nouns.txt",
                "src/languages/english-verbs.txt"
            );
            crosswordSolver.solve(crossword, lengths);
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println("Program terminated.");
            System.exit(1);
        }
    }
}
