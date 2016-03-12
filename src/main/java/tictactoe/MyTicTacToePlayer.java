package tictactoe;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 *
 * Constants
 * ***********************************
 * Character represents a mark on each cell
 * ------------------------------------
 * char BLANK = 'b', O = 'o', X = 'x'
 *
 *
 * Integer represents a number of cells in a column and row.
 * ------------------------------------
 * NUM_COL = 3, number of cells in a column.
 * NUM_ROW = 3, number of cells in a row.
 *
 *
 * Methods
 * ***********************************
 * Move selectTheMove(long timeout)
 * ------------------------------------
 * Implements your logic for each turn. In this method you need to call "mark(col, row)" to return Move.
 * Timeout is a time limit for each turn in milliseconds.
 *
 *
 * Move mark(int col, int row)
 * ------------------------------------
 * Marks the cell by given col and row, possible numbers for col, row in range = 1,2,3
 * Returns Move object.
 *
 *
 * char board(int col, int row)
 * ------------------------------------
 * Returns a symbol on a cell given col and row. BLANK, O, X
 *
 *
 * char getMySymbol()
 * ------------------------------------
 * Returns user's symbol, O or X.
 *
 *
 * char getOpponentSymbol()
 * ------------------------------------
 * Returns an opponent's symbol, O or X.
 *
 */
public class MyTicTacToePlayer extends TicTacToePlayer {


    // Finds a blank cell by each column, left to right.
    // Marks this blank cell, if a column of this cell does not have opponent's symbol.
    // Marks this blank cell, if a row of this cell does not have opponent's symbol.
    // Otherwise, marks on last blank cell.
    @Override
    Move selectTheMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        // 1 second before timeout
        long finishBy = timeout - 1000;

        Move move = mark(1, 1); // Initialize
        for (int col = 1; col <= NUM_COL; col++) {
            for (int row = 1; row <= NUM_ROW; row++) {
                if (board(col, row) == BLANK) {
                    move = mark(col, row);
                    // fills this cell, if about to reach timeout.
                    if (System.currentTimeMillis() > finishBy) return move;

                    // Marks that cell, if there are no opponent's mark in that column.
                    if (!isThereOpponentInColumn(col)) return move;
                    // Marks that cell, if there are no opponent's mark in that row.
                    if (!isThereOpponentInRow(row)) return move;
                }
            }
        }

        // never get here
        return move;
    }

    /*
     * True, if there is an opponent's mark on the column containing this cell.
     * False, otherwise.
     */
    Boolean isThereOpponentInColumn(int thisCol) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
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
    Boolean isThereOpponentInRow(int thisRow) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        // By Row
        for (int col = 1; col <= NUM_COL; col++) {
            if (board(col, thisRow) == getOpponentSymbol())
                return true;
        }
        return false;
    }

}
