package dev.zsithious.socialcues.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.zsithious.socialcues.core.state.Activity;

/**
 * DESIGN.md §7 P4a task note: "her Activity değerinin bir hücresi olduğunu
 * doğrulayan bir JUnit testi (yeni enum eklenirse derleme/test kırılsın)."
 * {@link CueIconAtlas#cellFor} is an exhaustive switch, so a future
 * {@code Activity} constant added without a case already fails to
 * <em>compile</em>; this test is the second line of defense, asserting the
 * resulting table's actual shape (in-range, distinct, and distinct from the
 * reserved SLEEPY cell).
 */
class CueIconAtlasTest {

    @Test
    void everyActivityHasAnInRangeCell() {
        int totalCells = CueIconAtlas.GRID_COLUMNS * CueIconAtlas.GRID_ROWS;
        for (Activity activity : Activity.values()) {
            int cell = CueIconAtlas.cellFor(activity);
            assertTrue(cell >= 0 && cell < totalCells, "cell out of range for " + activity + ": " + cell);
        }
    }

    @Test
    void everyActivityGetsADistinctCell() {
        Set<Integer> cells = new HashSet<>();
        for (Activity activity : Activity.values()) {
            assertTrue(cells.add(CueIconAtlas.cellFor(activity)),
                    "duplicate icon cell for " + activity);
        }
        assertEquals(Activity.values().length, cells.size());
    }

    @Test
    void sleepyCellIsInRangeAndDistinctFromEveryActivityCell() {
        int totalCells = CueIconAtlas.GRID_COLUMNS * CueIconAtlas.GRID_ROWS;
        assertTrue(CueIconAtlas.SLEEPY_CELL >= 0 && CueIconAtlas.SLEEPY_CELL < totalCells);

        for (Activity activity : Activity.values()) {
            assertTrue(CueIconAtlas.SLEEPY_CELL != CueIconAtlas.cellFor(activity),
                    "SLEEPY_CELL collides with " + activity + "'s cell");
        }
    }

    @Test
    void gridDimensionsMatchDesignDocEightByEight() {
        assertEquals(8, CueIconAtlas.GRID_COLUMNS);
        assertEquals(8, CueIconAtlas.GRID_ROWS);
    }

    @Test
    void textureSizeIsGridTimesCellPixels() {
        assertEquals(CueIconAtlas.GRID_COLUMNS * CueIconAtlas.CELL_PIXELS, CueIconAtlas.TEXTURE_WIDTH);
        assertEquals(CueIconAtlas.GRID_ROWS * CueIconAtlas.CELL_PIXELS, CueIconAtlas.TEXTURE_HEIGHT);
    }

    @Test
    void columnAndRowRoundTripEveryUsedCell() {
        int usedCells = Activity.values().length + 1; // + SLEEPY_CELL
        for (int cell = 0; cell < usedCells; cell++) {
            int column = CueIconAtlas.column(cell);
            int row = CueIconAtlas.row(cell);
            assertEquals(cell, row * CueIconAtlas.GRID_COLUMNS + column);
        }
    }

    @Test
    void uvBoundsAreOrderedAndWithinUnitRange() {
        int usedCells = Activity.values().length + 1;
        for (int cell = 0; cell < usedCells; cell++) {
            float minU = CueIconAtlas.minU(cell);
            float maxU = CueIconAtlas.maxU(cell);
            float minV = CueIconAtlas.minV(cell);
            float maxV = CueIconAtlas.maxV(cell);

            assertTrue(minU < maxU);
            assertTrue(minV < maxV);
            assertTrue(minU >= 0.0f && maxU <= 1.0f);
            assertTrue(minV >= 0.0f && maxV <= 1.0f);
        }
    }

    @Test
    void firstCellStartsAtOriginOfTexture() {
        assertEquals(0.0f, CueIconAtlas.minU(0));
        assertEquals(0.0f, CueIconAtlas.minV(0));
    }

    @Test
    void nullActivityRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class, () -> CueIconAtlas.cellFor(null));
    }
}
