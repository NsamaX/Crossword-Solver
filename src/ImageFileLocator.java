package src;

class ImageFileLocator {
    static String findFirstPngInCurrentDir() {
        java.io.File[] pngFiles = new java.io.File(".").listFiles(
            (dir, name) -> name.toLowerCase().endsWith(".png")
        );
        if (pngFiles == null || pngFiles.length == 0) {
            throw new IllegalStateException("No .png files found in current directory");
        }
        return pngFiles[0].getName();
    }
}
