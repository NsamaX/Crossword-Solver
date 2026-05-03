# Crossword Solver

Solves the Ludobros crossword puzzle game automatically from a screenshot. Detects letters via image recognition, then finds valid English word combinations using backtracking.

## Screenshots

![Screenshot](docs/thumbnail.png)

## Features

- **Image Reading**: Extracts the crossword grid and answer lengths directly from a `.png` screenshot.
- **Gravity Rule**: Simulates the game's letter-dropping mechanic as words are consumed.
- **Backtracking Solver**: Efficiently searches all word combinations to find valid solutions.
- **English Word Lists**: Covers common nouns and verbs (~4,700 words).

## Installation

1. **Clone the Repository**
   ```bash
   git clone https://github.com/nozomi-it-dev/Crossword-Solver.git
   ```

2. **Prerequisites**
   - Java 17 or later ([download](https://www.oracle.com/java/technologies/downloads/))

3. **Set Up**
   No additional setup required. Compiled `.class` files are included in `out/`.

   To recompile from source:
   ```bash
   javac -d out src/*.java
   ```

4. **Run**
   Place a `.png` screenshot of the crossword puzzle in the project root, then run:
   ```bash
   java -cp out src.Main
   ```
   The program picks the first `.png` file it finds in the current directory automatically.
   Results are printed to the console and saved to `results.txt`.

## Demo

| Input | Value |
|-------|-------|
| File | Any `.png` screenshot of the Ludobros crossword game |
| Output | Space-separated list of words, e.g. `TROUBLE RANGE PROCESS SISTER INFORMATION` |

## License

This project is licensed under the **MIT License**.
