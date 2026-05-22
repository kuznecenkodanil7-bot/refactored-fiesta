package com.morisnmoto.minesweeper;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

public final class MinesweeperScreen extends Screen {
    private enum Difficulty {
        EASY("Лёгкий", 9, 9, 10),
        NORMAL("Средний", 16, 16, 40),
        HARD("Сложный", 30, 16, 99);

        final String label;
        final int cols;
        final int rows;
        final int mines;

        Difficulty(String label, int cols, int rows, int mines) {
            this.label = label;
            this.cols = cols;
            this.rows = rows;
            this.mines = mines;
        }
    }

    private static final int LEFT_BUTTON = 0;
    private static final int RIGHT_BUTTON = 1;

    private final Random random = new Random();

    private Difficulty difficulty = Difficulty.EASY;
    private boolean[][] mines;
    private boolean[][] revealed;
    private boolean[][] flagged;
    private int[][] around;

    private boolean generated;
    private boolean gameOver;
    private boolean won;
    private int revealedSafeCells;
    private int flagsPlaced;

    private int cellSize;
    private int boardX;
    private int boardY;

    public MinesweeperScreen() {
        super(Text.literal("Сапёр"));
        newGame(Difficulty.EASY);
    }

    @Override
    protected void init() {
        int buttonWidth = 92;
        int buttonHeight = 20;
        int gap = 6;
        int total = Difficulty.values().length * buttonWidth + (Difficulty.values().length - 1) * gap;
        int startX = (this.width - total) / 2;
        int y = 32;

        int index = 0;
        for (Difficulty option : Difficulty.values()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal(option.label), button -> newGame(option))
                    .dimensions(startX + index * (buttonWidth + gap), y, buttonWidth, buttonHeight)
                    .build());
            index++;
        }

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Заново"), button -> newGame(difficulty))
                .dimensions(this.width / 2 - 47, this.height - 28, 94, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        updateLayout();
        this.renderBackground(context, mouseX, mouseY, deltaTicks);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Сапёр"), this.width / 2, 10, 0xFFFFFF);
        drawStatus(context);
        drawBoard(context, mouseX, mouseY);

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (mouseX < boardX || mouseY < boardY
                || mouseX >= boardX + difficulty.cols * cellSize
                || mouseY >= boardY + difficulty.rows * cellSize) {
            return false;
        }

        int col = (int) ((mouseX - boardX) / cellSize);
        int row = (int) ((mouseY - boardY) / cellSize);

        if (button == LEFT_BUTTON) {
            reveal(row, col);
            return true;
        }

        if (button == RIGHT_BUTTON) {
            toggleFlag(row, col);
            return true;
        }

        return false;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void newGame(Difficulty newDifficulty) {
        this.difficulty = newDifficulty;
        this.mines = new boolean[difficulty.rows][difficulty.cols];
        this.revealed = new boolean[difficulty.rows][difficulty.cols];
        this.flagged = new boolean[difficulty.rows][difficulty.cols];
        this.around = new int[difficulty.rows][difficulty.cols];
        this.generated = false;
        this.gameOver = false;
        this.won = false;
        this.revealedSafeCells = 0;
        this.flagsPlaced = 0;
    }

    private void updateLayout() {
        int maxWidthCell = Math.max(8, (this.width - 40) / difficulty.cols);
        int maxHeightCell = Math.max(8, (this.height - 118) / difficulty.rows);
        this.cellSize = Math.min(18, Math.min(maxWidthCell, maxHeightCell));
        this.boardX = (this.width - difficulty.cols * cellSize) / 2;
        this.boardY = 76;
    }

    private void drawStatus(DrawContext context) {
        String result;
        if (won) {
            result = "Победа! Все безопасные клетки открыты.";
        } else if (gameOver) {
            result = "Бум! Нажми «Заново» или выбери сложность.";
        } else {
            result = "Мины: " + difficulty.mines + "   Флаги: " + flagsPlaced + "   ПКМ — флаг, ЛКМ — открыть";
        }

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(result), this.width / 2, 58, 0xFFFFFF);
    }

    private void drawBoard(DrawContext context, int mouseX, int mouseY) {
        for (int row = 0; row < difficulty.rows; row++) {
            for (int col = 0; col < difficulty.cols; col++) {
                int x = boardX + col * cellSize;
                int y = boardY + row * cellSize;
                boolean hovered = mouseX >= x && mouseX < x + cellSize && mouseY >= y && mouseY < y + cellSize;

                drawCell(context, row, col, x, y, hovered);
            }
        }
    }

    private void drawCell(DrawContext context, int row, int col, int x, int y, boolean hovered) {
        int border = 0xFF101010;
        int hidden = hovered && !revealed[row][col] && !gameOver ? 0xFF737373 : 0xFF5A5A5A;
        int open = 0xFFB8B8B8;
        int mineColor = 0xFFB00020;
        int flagColor = 0xFFFFD54F;

        context.fill(x, y, x + cellSize, y + cellSize, border);

        if (revealed[row][col]) {
            context.fill(x + 1, y + 1, x + cellSize - 1, y + cellSize - 1, mines[row][col] ? mineColor : open);
            if (mines[row][col]) {
                drawCentered(context, "*", x, y, 0xFFFFFFFF);
            } else if (around[row][col] > 0) {
                drawCentered(context, String.valueOf(around[row][col]), x, y, numberColor(around[row][col]));
            }
            return;
        }

        context.fill(x + 1, y + 1, x + cellSize - 1, y + cellSize - 1, hidden);

        if (flagged[row][col]) {
            drawCentered(context, "⚑", x, y, flagColor);
        }
    }

    private void drawCentered(DrawContext context, String text, int x, int y, int color) {
        int textWidth = this.textRenderer.getWidth(text);
        int textX = x + (cellSize - textWidth) / 2;
        int textY = y + (cellSize - 8) / 2;
        context.drawTextWithShadow(this.textRenderer, text, textX, textY, color);
    }

    private int numberColor(int number) {
        return switch (number) {
            case 1 -> 0xFF1976D2;
            case 2 -> 0xFF388E3C;
            case 3 -> 0xFFD32F2F;
            case 4 -> 0xFF512DA8;
            case 5 -> 0xFF795548;
            case 6 -> 0xFF00838F;
            case 7 -> 0xFF000000;
            default -> 0xFF424242;
        };
    }

    private void reveal(int row, int col) {
        if (gameOver || flagged[row][col] || revealed[row][col]) {
            return;
        }

        if (!generated) {
            generateBoard(row, col);
        }

        if (mines[row][col]) {
            revealed[row][col] = true;
            gameOver = true;
            won = false;
            revealAllMines();
            return;
        }

        floodReveal(row, col);
        checkWin();
    }

    private void toggleFlag(int row, int col) {
        if (gameOver || revealed[row][col]) {
            return;
        }

        flagged[row][col] = !flagged[row][col];
        flagsPlaced += flagged[row][col] ? 1 : -1;
    }

    private void generateBoard(int safeRow, int safeCol) {
        int placed = 0;
        while (placed < difficulty.mines) {
            int row = random.nextInt(difficulty.rows);
            int col = random.nextInt(difficulty.cols);

            if (mines[row][col] || isProtectedStartCell(row, col, safeRow, safeCol)) {
                continue;
            }

            mines[row][col] = true;
            placed++;
        }

        for (int row = 0; row < difficulty.rows; row++) {
            for (int col = 0; col < difficulty.cols; col++) {
                around[row][col] = countMinesAround(row, col);
            }
        }

        generated = true;
    }

    private boolean isProtectedStartCell(int row, int col, int safeRow, int safeCol) {
        return Math.abs(row - safeRow) <= 1 && Math.abs(col - safeCol) <= 1;
    }

    private int countMinesAround(int row, int col) {
        int count = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) {
                    continue;
                }

                int nr = row + dr;
                int nc = col + dc;
                if (isInside(nr, nc) && mines[nr][nc]) {
                    count++;
                }
            }
        }
        return count;
    }

    private void floodReveal(int startRow, int startCol) {
        Queue<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{startRow, startCol});

        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            int row = point[0];
            int col = point[1];

            if (!isInside(row, col) || revealed[row][col] || flagged[row][col] || mines[row][col]) {
                continue;
            }

            revealed[row][col] = true;
            revealedSafeCells++;

            if (around[row][col] != 0) {
                continue;
            }

            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr != 0 || dc != 0) {
                        queue.add(new int[]{row + dr, col + dc});
                    }
                }
            }
        }
    }

    private void checkWin() {
        int safeCells = difficulty.rows * difficulty.cols - difficulty.mines;
        if (revealedSafeCells >= safeCells) {
            gameOver = true;
            won = true;
            revealAllMines();
        }
    }

    private void revealAllMines() {
        for (int row = 0; row < difficulty.rows; row++) {
            for (int col = 0; col < difficulty.cols; col++) {
                if (mines[row][col]) {
                    revealed[row][col] = true;
                }
            }
        }
    }

    private boolean isInside(int row, int col) {
        return row >= 0 && row < difficulty.rows && col >= 0 && col < difficulty.cols;
    }
}
