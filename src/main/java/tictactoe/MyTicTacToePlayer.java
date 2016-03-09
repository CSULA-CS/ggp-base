package tictactoe;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 *
 * Mark on each cell
 * ------------------
 * BLANK = 'b', O = 'o', X = 'x'
 *
 *
 * Number of cells
 * ------------------
 * NUM_COL = 3, number of cells in a column.
 * NUM_ROW = 3, number of cells in a row.
 *
 *
 * Methods
 * ***********************************
 * Move selectTheMove(long timeout)
 * ------------------------------------
 * Method to implement player logic.
 * Call method "mark" to get Move object required by this method.
 * timeout, needs to return Move before reaching timeout.
 *
 * Move mark(int col, int row)
 * ------------------------------------
 * Marks the cell by given col and row, possible numbers for col, row in range = 1,2,3
 * Returns Move object.
 *
 *
 * char board(int col, int row)
 * ------------------------------------
 * Returns a cell given col and row. BLANK, O, X
 *
 *
 * char getMySymbol()
 * ------------------------------------
 * Returns a role, O or X.
 *
 *
 * char getOpponentSymbol()
 * ------------------------------------
 * Returns an opponent role, O or X.
 *
 */
public class MyTicTacToePlayer extends TicTacToePlayer {

    @Override
    Move selectTheMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        // 1 second before timeout
        long finishBy = timeout - 1000;

        // If there are no opponent's mark in the column containing this cell, marks that cell.
        // Otherwise, marks on first blank cell by column from top to bottom.
        for (int col = 1; col <= NUM_COL; col++)
            for (int row = 1; row <= NUM_ROW; row++) {
                if (board(col, row) == BLANK) {
                    // fills this cell, if about to reach timeout.
                    if (System.currentTimeMillis() > finishBy) return mark(col, row);

                    // if no opponent's mark on the column and row containing this cell, marks this cell.
                    if (!isOpponentInColumn(col)) return mark(col, row);
                }
            }

        // otherwise, marks on first blank cell by column from top to bottom.
        for (int col = 1; col <= NUM_COL; col++)
            for (int row = 1; row <= NUM_ROW; row++) {
                // fills this cell, if about to reach timeout.
                if (System.currentTimeMillis() > finishBy) return mark(col, row);
                // fills any blank cell.
                if (board(col, row) == BLANK) return mark(col, row);
            }

        // never get here, because of the tictactoe rule.
        return mark(1, 1);
    }

    /*
     * True, if there is an opponent's mark on the column containing this cell.
     * False, otherwise.
     */
    Boolean isOpponentInColumn(int thisCol) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        // By column
        for (int row = 1; row <= NUM_ROW; row++) {
            if (board(thisCol, row) == getOpponentSymbol())
                return true;
        }
        return false;
    }

    /*
     * True, if there is an opponent's mark on the row containing this cell.
     * False, otherwise.
     */
    Boolean isOpponentInRow(int thisRow) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        // By column
        for (int col = 1; col <= NUM_COL; col++) {
            if (board(col, thisRow) == getOpponentSymbol())
                return true;
        }
        return false;
    }

}
