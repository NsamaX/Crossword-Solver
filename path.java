import java.util.ArrayList;

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
}