import java.util.ArrayList;

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

    public void addConnection(Node node) {
        connections.add(node);
    }
}