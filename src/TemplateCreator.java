package src;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.*;

public class TemplateCreator {
    
    static class Component {
        int minX, maxX, minY, maxY, pixelCount;
        
        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
        int centerX() { return (minX + maxX) / 2; }
        int centerY() { return (minY + maxY) / 2; }
        int area() { return width() * height(); }
        double density() { return (double) pixelCount / area(); }
    }
    
    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(60));
            System.out.println("TEMPLATE CREATOR");
            System.out.println("=".repeat(60));
            
            String imagePath = ImageFileLocator.findFirstPngInCurrentDir();
            String answerPath = "src/TemplateAnswer.txt";
            String outputDir = "src/templates/";
            
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                System.err.println("ERROR: File not found: " + imagePath);
                return;
            }
            
            File answerFile = new File(answerPath);
            if (!answerFile.exists()) {
                System.err.println("ERROR: File not found: " + answerPath);
                return;
            }
            
            List<String> correctAnswers = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(answerFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        correctAnswers.add(line);
                    }
                }
            }
            
            if (correctAnswers.isEmpty()) {
                System.err.println("ERROR: File " + answerPath + " is empty");
                return;
            }
            
            System.out.println("Loaded " + correctAnswers.size() + " answer row(s) from " + answerPath);
            
            new File(outputDir).mkdirs();
            
            BufferedImage image = ImageIO.read(imageFile);
            int threshold = findBestThreshold(image);
            boolean[][] mask = buildMask(image, threshold);
            List<Component> allComponents = extractComponents(mask);
            List<Component> letterComponents = filterLetterComponents(
                allComponents, image.getWidth(), image.getHeight()
            );
            
            List<Component> gridComponents = new ArrayList<>();
            int imgHeight = image.getHeight();
            for (Component c : letterComponents) {
                double relativeY = (double) c.centerY() / imgHeight;
                if (relativeY > 0.20 && relativeY < 0.65) {
                    gridComponents.add(c);
                }
            }
            
            if (gridComponents.isEmpty()) {
                gridComponents = letterComponents;
            }
            
            if (gridComponents.isEmpty()) {
                gridComponents = allComponents;
            }
            
            List<List<Component>> rows = arrangeIntoRows(gridComponents);
            if (!rows.isEmpty()) {
                List<Integer> sizes = new ArrayList<>();
                for (List<Component> row : rows) sizes.add(row.size());
                Collections.sort(sizes);
                int typical = sizes.get(sizes.size() / 2);
                List<List<Component>> filteredRows = new ArrayList<>();
                for (List<Component> row : rows) {
                    if (row.size() >= Math.max(1, typical - 1) && row.size() <= typical + 1) {
                        filteredRows.add(row);
                    }
                }
                if (filteredRows.size() > 6) {
                    filteredRows = filteredRows.subList(0, 6);
                }
                rows = filteredRows;
            }
            
            if (rows.isEmpty()) {
                System.err.println("ERROR: Unable to arrange components into rows");
                return;
            }
            
            int numRows = rows.size();
            int numCols = 0;
            for (List<Component> row : rows) {
                numCols = Math.max(numCols, row.size());
            }
            
            System.out.println("Detected " + numRows + " row(s), " + numCols + " column(s)");
            System.out.println();
            
            System.out.println("Answers from answer.txt:");
            for (int i = 0; i < correctAnswers.size(); i++) {
                System.out.println("  Row " + i + ": " + correctAnswers.get(i));
            }
            System.out.println();
            
            Scanner scanner = new Scanner(System.in);
            boolean autoMode = Arrays.asList(args).contains("--auto");
            if (!autoMode) {
                System.out.print("Use answers from answer.txt automatically? (y/n): ");
                String choice = scanner.nextLine().trim().toLowerCase();
                autoMode = choice.equals("y") || choice.equals("yes");
            }
            
            if (autoMode) {
                System.out.println("✓ Auto mode enabled");
            } else {
                System.out.println("✓ Manual verification mode enabled");
                System.out.println("  Type a letter (A-Z), or 'skip', 'quit', 'auto' to switch to auto mode");
            }
            
            System.out.println("=".repeat(60));
            System.out.println();
            
            Map<Character, BufferedImage> templates = new HashMap<>();
            
            for (int r = 0; r < rows.size(); r++) {
                List<Component> row = rows.get(r);
                row.sort(Comparator.comparingInt(Component::centerX));
                
                char[] correctRow = new char[row.size()];
                if (r < correctAnswers.size()) {
                    String answer = correctAnswers.get(r).replaceAll("\\s+", "");
                    for (int i = 0; i < Math.min(answer.length(), correctRow.length); i++) {
                        correctRow[i] = answer.charAt(i);
                    }
                }
                
                for (int c = 0; c < row.size(); c++) {
                    Component comp = row.get(c);
                    BufferedImage cellImg = extractCell(image, comp);
                    
                    char correctLetter = c < correctRow.length ? correctRow[c] : '?';
                    
                    System.out.print("[Row " + r + "/" + (numRows-1) + 
                                   ", Col " + c + "/" + (row.size()-1) + "]");
                    if (correctLetter != '\0' && correctLetter != '?') {
                        System.out.print(" Hint: " + correctLetter);
                    }
                    
                    String input;
                    if (autoMode) {
                        if (correctLetter != '\0' && correctLetter != '?') {
                            input = String.valueOf(correctLetter);
                            System.out.println(" → " + input + " (auto)");
                        } else {
                            System.out.print(" → ");
                            input = scanner.nextLine().trim().toUpperCase();
                        }
                    } else {
                        System.out.print(" → ");
                        input = scanner.nextLine().trim().toUpperCase();
                    }
                    
                    if (input.equals("QUIT")) {
                        scanner.close();
                        System.out.println("\nCancelled.");
                        return;
                    }
                    
                    if (input.equals("AUTO")) {
                        autoMode = true;
                        if (correctLetter != '\0' && correctLetter != '?') {
                            input = String.valueOf(correctLetter);
                            System.out.println("  → " + input + " (auto mode enabled)");
                        } else {
                            System.out.print("  Please enter letter: ");
                            input = scanner.nextLine().trim().toUpperCase();
                        }
                    }
                    
                    if (input.equals("SKIP") || input.isEmpty()) {
                        continue;
                    }
                    
                    if (input.length() != 1 || !Character.isLetter(input.charAt(0))) {
                        System.out.println("  (Skipped - invalid input)");
                        continue;
                    }
                    
                    char letter = input.charAt(0);
                    
                    if (!autoMode && correctLetter != '\0' && correctLetter != '?' 
                        && letter != correctLetter) {
                        System.out.print("  ⚠ You typed '" + letter + 
                            "' but the answer is '" + correctLetter + "'. Confirm? (y/n): ");
                        String confirm = scanner.nextLine().trim().toLowerCase();
                        if (!confirm.equals("y") && !confirm.equals("yes")) {
                            continue;
                        }
                    }
                    
                    if (!templates.containsKey(letter)) {
                        templates.put(letter, cellImg);
                        File templateFile = new File(outputDir + letter + ".png");
                        ImageIO.write(cellImg, "png", templateFile);
                        if (!autoMode) {
                            System.out.println("  ✓ Saved " + letter + ".png");
                        }
                    } else {
                        if (!autoMode) {
                            System.out.println("  (" + letter + " already exists)");
                        }
                    }
                }
            }
            
            scanner.close();
            
            System.out.println("\n" + "=".repeat(60));
            System.out.println("Completed!");
            System.out.println("=".repeat(60));
            System.out.println("Templates created: " + templates.size());
            
            List<Character> sortedKeys = new ArrayList<>(templates.keySet());
            Collections.sort(sortedKeys);
            System.out.print("Available: ");
            for (char ch : sortedKeys) {
                System.out.print(ch + " ");
            }
            System.out.println();
            
            Set<Character> allLetters = new HashSet<>();
            for (char ch = 'A'; ch <= 'Z'; ch++) {
                allLetters.add(ch);
            }
            allLetters.removeAll(templates.keySet());
            
            if (!allLetters.isEmpty()) {
                List<Character> missing = new ArrayList<>(allLetters);
                Collections.sort(missing);
                System.out.print("Missing templates in " + outputDir + " for letters: ");
                for (char ch : missing) {
                    System.out.print(ch + " ");
                }
                System.out.println();
            } else {
                System.out.println("All A-Z templates exist in " + outputDir);
            }
            
            System.out.println("\nSaved to: " + outputDir);
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static int findBestThreshold(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] histogram = new int[256];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                histogram[gray]++;
            }
        }
        
        int total = width * height;
        float sum = 0;
        for (int i = 0; i < 256; i++) sum += i * histogram[i];
        
        float sumB = 0;
        int wB = 0, wF;
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
        
        return Math.max(threshold, 100);
    }
    
    private static boolean[][] buildMask(BufferedImage image, int threshold) {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean[][] mask = new boolean[height][width];
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = (((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF)) / 3;
                mask[y][x] = gray > threshold;
            }
        }
        return mask;
    }
    
    private static List<Component> extractComponents(boolean[][] mask) {
        int height = mask.length;
        int width = mask[0].length;
        boolean[][] visited = new boolean[height][width];
        List<Component> components = new ArrayList<>();
        int[] dx = {-1, 0, 1, 0};
        int[] dy = {0, -1, 0, 1};
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!mask[y][x] || visited[y][x]) continue;
                
                Component comp = new Component();
                comp.minX = comp.maxX = x;
                comp.minY = comp.maxY = y;
                comp.pixelCount = 0;
                
                List<int[]> queue = new ArrayList<>();
                queue.add(new int[]{x, y});
                visited[y][x] = true;
                
                for (int i = 0; i < queue.size(); i++) {
                    int[] p = queue.get(i);
                    int cx = p[0], cy = p[1];
                    comp.pixelCount++;
                    comp.minX = Math.min(comp.minX, cx);
                    comp.maxX = Math.max(comp.maxX, cx);
                    comp.minY = Math.min(comp.minY, cy);
                    comp.maxY = Math.max(comp.maxY, cy);
                    
                    for (int d = 0; d < 4; d++) {
                        int nx = cx + dx[d], ny = cy + dy[d];
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
    
    private static List<Component> filterLetterComponents(
            List<Component> components, int imgWidth, int imgHeight) {
        
        if (components.isEmpty()) return components;
        
        List<Integer> widths = new ArrayList<>();
        for (Component c : components) widths.add(c.width());
        Collections.sort(widths);
        
        int standardWidth = widths.get((int)(widths.size() * 0.80));
        List<Component> filtered = new ArrayList<>();
        
        for (Component c : components) {
            double aspectRatio = (double) c.width() / c.height();
            double widthRatio = (double) c.width() / standardWidth;
            
            if (aspectRatio >= 0.90 && aspectRatio <= 1.10 &&
                widthRatio >= 0.85 && widthRatio <= 1.15 &&
                c.width() >= 50 && c.height() >= 50 &&
                c.density() >= 0.15) {
                filtered.add(c);
            }
        }
        return filtered;
    }
    
    private static List<List<Component>> arrangeIntoRows(List<Component> components) {
        if (components.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Component> sorted = new ArrayList<>(components);
        sorted.sort(Comparator.comparingInt(Component::centerY));
        
        List<Integer> heights = new ArrayList<>();
        for (Component c : sorted) heights.add(c.height());
        Collections.sort(heights);
        
        int medianHeight = heights.isEmpty() ? 100 : heights.get(heights.size() / 2);
        double tolerance = medianHeight * 0.5;
        
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
        if (!currentRow.isEmpty()) rows.add(currentRow);
        
        return rows;
    }
    
    private static BufferedImage extractCell(BufferedImage image, Component comp) {
        int w = comp.width();
        int h = comp.height();
        
        BufferedImage cell = image.getSubimage(comp.minX, comp.minY, w, h);
        
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = result.createGraphics();
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, w, h);
        g2d.drawImage(cell, 0, 0, null);
        g2d.dispose();
        
        return result;
    }
}
