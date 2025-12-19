class Main {
    public static void main(String[] args) {
        String[] crossword = {
            "avheiw",
            "dgetlr",
            "aacaco",
            "tsetta",
            "nueoec",
            "trdnpe"
        };
        int[] lengths = {5,7,9,6,9};
        try {
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
            CrosswordSolver crosswordSolver = new CrosswordSolver(
                "languages/english-nouns.txt",
                "languages/english-verbs.txt"
            );
            crosswordSolver.solve(crossword, lengths);
        } catch (IllegalArgumentException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.err.println("Program terminated.");
            System.exit(1);
        }
    }
}