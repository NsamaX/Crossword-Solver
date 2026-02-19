package src;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class CrosswordReader {
    private static final boolean DEBUG = false;
    private static final String TEMPLATE_DIR = "src/templates";
    private static final int TEMPLATE_SIZE = 64;
    private static java.util.Map<Character, double[][]> TEMPLATE_CACHE;
    
    static class Result {
        private final String[] crossword;
        private final int[] lengths;

        Result(String[] crossword, int[] lengths) {
            this.crossword = crossword;
            this.lengths = lengths;
        }

        String[] getCrossword() {
            return crossword;
        }

        int[] getLengths() {
            return lengths;
        }
    }

    static class Component {
        int minX;
        int maxX;
        int minY;
        int maxY;
        int pixelCount;

        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }

        int centerX() {
            return (minX + maxX) / 2;
        }

        int centerY() {
            return (minY + maxY) / 2;
        }
        
        int area() {
            return width() * height();
        }
        
        double density() {
            return (double) pixelCount / area();
        }
        
        @Override
        public String toString() {
            return String.format("Component[pos=(%d,%d) size=%dx%d pixels=%d density=%.2f]", 
                centerX(), centerY(), width(), height(), pixelCount, density());
        }
    }

    static Result readFromImage(String imagePath) {
        try {
            System.out.println("\n========================================");
            System.out.println("CROSSWORD IMAGE READER");
            if (DEBUG) {
                System.out.println("Mode: DEBUG");
            }
            System.out.println("========================================");
            System.out.println("Input image: " + imagePath);
            
            BufferedImage image = ImageIO.read(new File(imagePath));
            if (image == null) {
                throw new IllegalArgumentException("Cannot read image: " + imagePath);
            }
            
            ensureTemplatesLoaded();
            
            int imgWidth = image.getWidth();
            int imgHeight = image.getHeight();
            System.out.println("Image size: " + imgWidth + " x " + imgHeight);
            
            analyzeImageColors(image);
            
            List<Component> allComponents = new ArrayList<>();
            int bestThreshold = findBestThreshold(image);
            
            System.out.println("\n--- Using threshold: " + bestThreshold + " ---");
            boolean[][] mask = buildMask(image, bestThreshold);
            allComponents = extractComponents(mask);
            
            System.out.println("\nFound " + allComponents.size() + " components");
            
            List<Component> letterLikeComponents = filterLetterComponents(allComponents, imgWidth, imgHeight);
            System.out.println("Letter-like components: " + letterLikeComponents.size());
            
            List<Component> gridComponents = new ArrayList<>();
            List<Component> answerComponents = new ArrayList<>();
            
            for (Component c : letterLikeComponents) {
                double relativeY = (double) c.centerY() / imgHeight;
                
                if (relativeY > 0.15 && relativeY < 0.70) {
                    gridComponents.add(c);
                } else if (relativeY >= 0.70 && relativeY < 0.95) {
                    answerComponents.add(c);
                }
            }
            
            System.out.println("\nGrid zone components: " + gridComponents.size());
            System.out.println("Answer zone components: " + answerComponents.size());
            
            if (gridComponents.isEmpty()) {
                System.out.println("\nWARNING: No grid components found in expected zone");
                System.out.println("Trying all letter-like components...");
                gridComponents = new ArrayList<>(letterLikeComponents);
            }
            
            if (gridComponents.isEmpty()) {
                throw new IllegalStateException(
                    "Cannot detect any text cells. Found " + allComponents.size() + " total components.");
            }
            
            String[] crossword = readGridLetters(image, gridComponents);
            int totalLetters = 0;
            for (String row : crossword) {
                totalLetters += row.length();
            }
            int[] lengths = answerComponents.isEmpty() ? new int[0] : computeAnswerLengths(answerComponents, totalLetters);
            
            System.out.println("\n========================================");
            System.out.println("RESULT:");
            System.out.println("========================================");
            for (int i = 0; i < crossword.length; i++) {
                System.out.println("Row " + i + ": [" + crossword[i] + "]");
            }
            System.out.println("\nAnswer lengths: " + Arrays.toString(lengths));
            System.out.println("========================================\n");
            
            return new Result(crossword, lengths);
            
        } catch (IOException e) {
            throw new RuntimeException("Error reading image: " + e.getMessage(), e);
        }
    }
    
    private static void analyzeImageColors(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int minBright = 255, maxBright = 0;
        long sumBright = 0;
        int[] histogram = new int[256];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r + g + b) / 3;
                
                histogram[gray]++;
                minBright = Math.min(minBright, gray);
                maxBright = Math.max(maxBright, gray);
                sumBright += gray;
            }
        }
        
        int avgBright = (int) (sumBright / (width * height));
        
        System.out.println("\nColor Analysis:");
        System.out.println("  Brightness range: " + minBright + " - " + maxBright);
        System.out.println("  Average brightness: " + avgBright);
    }
    
    private static int findBestThreshold(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r + g + b) / 3;
                histogram[gray]++;
            }
        }
        
        int total = width * height;
        float sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }
        
        float sumB = 0;
        int wB = 0;
        int wF = 0;
        float maxVariance = 0;
        int threshold = 0;
        
        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            
            wF = total - wB;
            if (wF == 0) break;
            
            sumB += t * histogram[t];
            
            float mB = sumB / wB;
            float mF = (sum - sumB) / wF;
            
            float variance = wB * wF * (mB - mF) * (mB - mF);
            
            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = t;
            }
        }
        
        if (threshold < 100) {
            threshold = 120;
        }
        
        return threshold;
    }
    
    private static boolean[][] buildMask(BufferedImage image, int threshold) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] mask = new boolean[height][width];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r + g + b) / 3;
                
                mask[y][x] = gray > threshold;
            }
        }
        
        return mask;
    }
    
    static List<Component> filterLetterComponents(List<Component> components, int imgWidth, int imgHeight) {
        if (components.isEmpty()) return components;
        
        List<Integer> widths = new ArrayList<>();
        List<Integer> heights = new ArrayList<>();
        
        for (Component c : components) {
            widths.add(c.width());
            heights.add(c.height());
        }
        
        Collections.sort(widths);
        Collections.sort(heights);
        
        int targetIndex = (int) (widths.size() * 0.80);
        int standardWidth = widths.get(Math.min(targetIndex, widths.size() - 1));
        int standardHeight = heights.get(Math.min(targetIndex, heights.size() - 1));
        
        System.out.println("\nFiltering components:");
        System.out.println("Standard letter size (80th percentile): " + standardWidth + "x" + standardHeight);
        
        List<Component> filtered = new ArrayList<>();
        
        for (Component c : components) {
            boolean isValid = true;
            String reason = "";
            
            double aspectRatio = (double) c.width() / c.height();
            if (aspectRatio > 1.3 || aspectRatio < 0.77) {
                isValid = false;
                reason = "not square (aspect ratio: " + String.format("%.2f", aspectRatio) + ")";
            }
            
            double widthRatio = (double) c.width() / standardWidth;
            if (widthRatio < 0.4 || widthRatio > 1.2) {
                isValid = false;
                reason = "width too different (" + c.width() + " vs " + standardWidth + ", ratio=" + String.format("%.2f", widthRatio) + ")";
            }
            
            if (c.width() < 50 || c.height() < 50) {
                isValid = false;
                reason = "too small (< 50px)";
            }
            
            if (c.density() < 0.15) {
                isValid = false;
                reason = "too low density: " + String.format("%.2f", c.density());
            }
            
            if (!isValid) {
                System.out.println("  FILTERED: " + c + " - " + reason);
            }
            
            if (isValid) {
                filtered.add(c);
            }
        }
        
        System.out.println("Kept " + filtered.size() + " / " + components.size() + " components");
        
        return filtered;
    }

    static List<Component> extractComponents(boolean[][] mask) {
        int height = mask.length;
        int width = mask[0].length;
        boolean[][] visited = new boolean[height][width];
        List<Component> components = new ArrayList<>();
        int[] dx = {-1, 0, 1, 0};
        int[] dy = {0, -1, 0, 1};
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y][x] || visited[y][x]) {
                    continue;
                }
                
                Component comp = new Component();
                comp.minX = x;
                comp.maxX = x;
                comp.minY = y;
                comp.maxY = y;
                comp.pixelCount = 0;
                
                List<int[]> queue = new ArrayList<>();
                queue.add(new int[]{x, y});
                visited[y][x] = true;
                int index = 0;
                
                while (index < queue.size()) {
                    int[] p = queue.get(index++);
                    int cx = p[0];
                    int cy = p[1];
                    comp.pixelCount++;
                    
                    comp.minX = Math.min(comp.minX, cx);
                    comp.maxX = Math.max(comp.maxX, cx);
                    comp.minY = Math.min(comp.minY, cy);
                    comp.maxY = Math.max(comp.maxY, cy);
                    
                    for (int d = 0; d < dx.length; d++) {
                        int nx = cx + dx[d];
                        int ny = cy + dy[d];
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            if (mask[ny][nx] && !visited[ny][nx]) {
                                visited[ny][nx] = true;
                                queue.add(new int[]{nx, ny});
                            }
                        }
                    }
                }
                
                components.add(comp);
            }
        }
        
        return components;
    }

    private static int[] computeAnswerLengths(List<Component> answerComponents, int totalLetters) {
        if (answerComponents.isEmpty()) {
            return new int[0];
        }
        
        List<Component> sorted = new ArrayList<>(answerComponents);
        sorted.sort(Comparator.comparingInt(Component::centerY));
        
        List<Integer> heights = new ArrayList<>();
        for (Component c : sorted) {
            heights.add(c.height());
        }
        Collections.sort(heights);
        int medianHeight = heights.get(heights.size() / 2);
        double tolerance = medianHeight * 1.0;
        
        if (DEBUG) {
            System.out.println("\nComputing answer lengths:");
            System.out.println("  Median height: " + medianHeight + ", tolerance: " + tolerance);
        }
        
        List<List<Component>> rows = new ArrayList<>();
        List<Component> currentRow = new ArrayList<>();
        
        for (Component c : sorted) {
            if (currentRow.isEmpty()) {
                currentRow.add(c);
            } else {
                Component last = currentRow.get(currentRow.size() - 1);
                if (Math.abs(c.centerY() - last.centerY()) <= tolerance) {
                    currentRow.add(c);
                } else {
                    rows.add(new ArrayList<>(currentRow));
                    if (DEBUG) {
                        System.out.println("  Row " + rows.size() + ": " + currentRow.size() + " cells");
                    }
                    currentRow.clear();
                    currentRow.add(c);
                }
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(new ArrayList<>(currentRow));
            if (DEBUG) {
                System.out.println("  Row " + rows.size() + ": " + currentRow.size() + " cells");
            }
        }
        
        List<Integer> lengthsList = new ArrayList<>();
        
        for (int i = 0; i < rows.size(); i++) {
            List<Component> row = rows.get(i);
            if (row.isEmpty()) {
                continue;
            }
            row.sort(Comparator.comparingInt(Component::centerX));
            
            if (row.size() == 1) {
                lengthsList.add(1);
                continue;
            }
            
            List<Integer> gaps = new ArrayList<>();
            for (int j = 1; j < row.size(); j++) {
                int gap = row.get(j).centerX() - row.get(j - 1).centerX();
                gaps.add(gap);
            }
            Collections.sort(gaps);
            int medianGap = gaps.get(gaps.size() / 2);
            double gapThreshold = medianGap * 1.7;
            
            int currentLen = 1;
            for (int j = 1; j < row.size(); j++) {
                Component prev = row.get(j - 1);
                Component curr = row.get(j);
                int gap = curr.centerX() - prev.centerX();
                if (gap > gapThreshold) {
                    lengthsList.add(currentLen);
                    currentLen = 1;
                } else {
                    currentLen++;
                }
            }
            lengthsList.add(currentLen);
        }
        
        int sum = 0;
        for (int len : lengthsList) {
            sum += len;
        }
        
        if (totalLetters > 0 && sum > totalLetters) {
            int diff = sum - totalLetters;
            boolean[] remove = new boolean[lengthsList.size()];
            List<Integer> idx = new ArrayList<>();
            for (int i = 0; i < lengthsList.size(); i++) {
                idx.add(i);
            }
            idx.sort(Comparator.comparingInt(lengthsList::get));
            for (int i : idx) {
                if (diff <= 0) break;
                int len = lengthsList.get(i);
                if (len <= diff) {
                    remove[i] = true;
                    diff -= len;
                }
            }
            List<Integer> adjusted = new ArrayList<>();
            for (int i = 0; i < lengthsList.size(); i++) {
                if (!remove[i]) {
                    adjusted.add(lengthsList.get(i));
                }
            }
            lengthsList = adjusted;
        }
        
        int[] lengths = new int[lengthsList.size()];
        for (int i = 0; i < lengthsList.size(); i++) {
            lengths[i] = lengthsList.get(i);
        }
        
        return lengths;
    }

    private static String[] readGridLetters(BufferedImage image, List<Component> gridComponents) {
        if (DEBUG) {
            System.out.println("\n--- Grouping into rows ---");
        }
        
        List<Component> sorted = new ArrayList<>(gridComponents);
        sorted.sort(Comparator.comparingInt(Component::centerY));
        
        List<Integer> heights = new ArrayList<>();
        for (Component c : sorted) {
            heights.add(c.height());
        }
        Collections.sort(heights);
        int medianHeight = heights.get(heights.size() / 2);
        double tolerance = medianHeight * 0.8;
        
        if (DEBUG) {
            System.out.println("Median height: " + medianHeight + ", tolerance: " + tolerance);
        }
        
        List<List<Component>> rows = new ArrayList<>();
        List<Component> currentRow = new ArrayList<>();
        
        for (Component c : sorted) {
            if (currentRow.isEmpty()) {
                currentRow.add(c);
            } else {
                Component last = currentRow.get(currentRow.size() - 1);
                if (Math.abs(c.centerY() - last.centerY()) <= tolerance) {
                    currentRow.add(c);
                } else {
                    rows.add(new ArrayList<>(currentRow));
                    currentRow.clear();
                    currentRow.add(c);
                }
            }
        }
        if (!currentRow.isEmpty()) {
            rows.add(currentRow);
        }
        
        if (DEBUG) {
            System.out.println("Found " + rows.size() + " rows");
        }
        
        String[] crossword = new String[rows.size()];
        
        for (int i = 0; i < rows.size(); i++) {
            List<Component> row = rows.get(i);
            row.sort(Comparator.comparingInt(Component::centerX));
            
            if (DEBUG) {
                System.out.println("\nRow " + i + ": " + row.size() + " cells");
            }
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < row.size(); j++) {
                Component c = row.get(j);
                char ch = recognizeLetter(image, c, i, j);
                sb.append(ch);
            }
            crossword[i] = sb.toString();
        }
        
        return crossword;
    }

    private static char recognizeLetter(BufferedImage image, Component comp, int row, int col) {
        try {
            ensureTemplatesLoaded();
            int w = comp.width();
            int h = comp.height();
            if (DEBUG) {
                System.out.println("  Cell [" + row + "," + col + "] " + w + "x" + h);
            }
            BufferedImage sub = image.getSubimage(comp.minX, comp.minY, w, h);
            double[][] cell = toGrayMatrix(sub, TEMPLATE_SIZE);
            char bestChar = '?';
            double bestScore = Double.MAX_VALUE;
            for (java.util.Map.Entry<Character, double[][]> entry : TEMPLATE_CACHE.entrySet()) {
                char ch = entry.getKey();
                double[][] tmpl = entry.getValue();
                double score = 0.0;
                for (int y = 0; y < TEMPLATE_SIZE; y++) {
                    double[] rowCell = cell[y];
                    double[] rowTmpl = tmpl[y];
                    for (int x = 0; x < TEMPLATE_SIZE; x++) {
                        double d = rowCell[x] - rowTmpl[x];
                        score += d * d;
                    }
                }
                score /= (double) (TEMPLATE_SIZE * TEMPLATE_SIZE);
                if (score < bestScore) {
                    bestScore = score;
                    bestChar = ch;
                }
            }
            double threshold = 0.06;
            if (DEBUG) {
                System.out.println("    best score=" + bestScore + " -> '" + bestChar + "'");
                if (bestScore > threshold) {
                    System.out.println("    -> '?' (score above threshold)");
                }
            }
            if (bestScore > threshold) {
                return '?';
            }
            return bestChar;
        } catch (Exception e) {
            System.err.println("    ERROR: " + e.getMessage());
            return '?';
        }
    }
    
    private static void ensureTemplatesLoaded() throws IOException {
        if (TEMPLATE_CACHE != null) {
            return;
        }
        TEMPLATE_CACHE = new java.util.HashMap<>();
        for (char ch = 'A'; ch <= 'Z'; ch++) {
            File f = new File(TEMPLATE_DIR + File.separator + ch + ".png");
            if (!f.exists()) {
                continue;
            }
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                continue;
            }
            double[][] data = toGrayMatrix(img, TEMPLATE_SIZE);
            TEMPLATE_CACHE.put(ch, data);
        }
        if (DEBUG) {
            System.out.println("Loaded templates from " + TEMPLATE_DIR + ": " + TEMPLATE_CACHE.keySet());
        }
        if (TEMPLATE_CACHE.isEmpty()) {
            throw new IllegalStateException("No templates found in " + TEMPLATE_DIR);
        }
    }
    
    private static double[][] toGrayMatrix(BufferedImage img, int size) {
        BufferedImage scaled = resizeImage(img, size, size);
        double[][] result = new double[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int rgb = scaled.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (r + g + b) / 3;
                result[y][x] = gray / 255.0;
            }
        }
        return result;
    }
    
    private static BufferedImage resizeImage(BufferedImage src, int w, int h) {
        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = dst.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.drawImage(src, 0, 0, w, h, null);
        g2d.dispose();
        return dst;
    }
}
