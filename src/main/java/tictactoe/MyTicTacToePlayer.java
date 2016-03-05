package tictactoe;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * Admin writes TicTacToePlayer, users extend this TicTacToePlayer.
 */
public class MyTicTacToePlayer extends TicTacToePlayer {
    /* sth
     *
     * char board(int col, int row)
     * Returns char at a position (column, row) on the grid.
     * 'b' = blank, 'x' and 'o'.
     * --------------------------------
     * char getMyRole()
     * Returns a role 'x' or 'o'.
     * --------------------------------
     * Move mark(int col, int row)
     * Marks 'x' or 'o' on the grid and returns Move object required by GGP.
     * The Move object will be translated into GDL term.
     *
     * noopMove to return 'noop'
     */


    @Override
    Move selectTheMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        long finishBy = timeout - 1000;
        Move move = winColumn(finishBy);
        if (move == noopMove) move = winRow(finishBy);
        if (move == noopMove) move = protect(finishBy);

        // Picks first blank cell.
        if (move == noopMove) {
            for (int col = 1; col <= MAX_CELLS; col++)
                for (int row = 1; row <= MAX_CELLS; row++)
                    if (board(col, row) == BLANK) return mark(col, row);
        }
        return move;
    }

    Move winColumn(long finishBy) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        int numMyMark, emptyCol, emptyRow;
        for (int col = 1; col <= MAX_CELLS; col++) {
            numMyMark = 0;
            emptyCol = 0;
            emptyRow = 0;
            for (int row = 1; row <= MAX_CELLS; row++) {
                if (System.currentTimeMillis() > finishBy) return mark(col, row);
                if (board(col, row) == getMyRole()) numMyMark++;
                else if (board(col, row) == BLANK) {
                    emptyCol = col;
                    emptyRow = row;
                }
            }
            if (numMyMark == 2 && emptyCol > 0) return mark(emptyCol, emptyRow);
        }
        return noopMove;
    }

    Move winRow(long finishBy) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        int numMyMark, emptyCol, emptyRow;
        for (int row = 1; row <= MAX_CELLS; row++) {
            numMyMark = 0;
            emptyCol = 0;
            emptyRow = 0;
            for (int col = 1; col <= MAX_CELLS; col++) {
                if (System.currentTimeMillis() > finishBy) return mark(col, row);
                if (board(col, row) == getMyRole()) numMyMark++;
                else if (board(col, row) == BLANK) {
                    emptyCol = col;
                    emptyRow = row;
                }
            }
            if (numMyMark == 2 && emptyCol > 0) return mark(emptyCol, emptyRow);
        }
        return noopMove;
    }

    Move protect(long finishBy) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        Move move = protectColumns(finishBy);
        if (move == noopMove) move = protectRows(finishBy);
        if (move == noopMove) move = protectDiagonals(finishBy);
        return move;
    }

    Move protectRows(long finishBy) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        int opponentCells;
        int blankCol;
        int blankRow;
        // protect rows
        for (int row = 1; row <= NUM_ROW; row++) {
            opponentCells = 0;
            blankCol = 0;
            blankRow = 0;
            for (int col = 1; col <= NUM_COL; col++) {
                if (System.currentTimeMillis() > finishBy) {
                    System.out.println("no time to think! pick one move.");
                    return mark(col, row);
                }

                if (board(col, row) == getOpponentRole()) opponentCells++;
                if (board(col, row) == 'b') {
                    blankCol = col;
                    blankRow = row;
                }
            }
            if (opponentCells == 2 && blankCol > 0 && blankRow > 0) {
                System.out.println("protectRows");
                return mark(blankCol, blankRow);
            }

        }
        return noopMove;
    }

    Move protectColumns(long finishBy) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        int opponentCells;
        int blankCol;
        int blankRow;

        // protect columns
        for (int col = 1; col <= NUM_COL; col++) {
            opponentCells = 0;
            blankCol = 0;
            blankRow = 0;
            for (int row = 1; row <= NUM_ROW; row++) {
                if (System.currentTimeMillis() > finishBy) {
                    System.out.println("no time to think! pick one move.");
                    return mark(col, row);
                }

                if (board(col, row) == getOpponentRole()) {
                    System.out.println("col = " + col + ", row = " + row);
                    opponentCells++;
                }

                if (board(col, row) == BLANK) {
                    blankCol = col;
                    blankRow = row;
                }
            }
            if (opponentCells == 2 && blankCol > 0 && blankRow > 0) {
                System.out.println("protectColumns");
                return mark(blankCol, blankRow);
            }

        }
        return noopMove;
    }

    Move protectDiagonals(long finishBy) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        // top left to bottom right
        int opponentCells = 0;
        int blankCol = 0;
        int blankRow = 0;
        for (int i = 1; i <= MAX_CELLS; i++) {
            if (System.currentTimeMillis() > finishBy) {
                System.out.println("no time to think! pick one move.");
                return mark(i, i);
            }

            if (board(i, i) == getOpponentRole()) opponentCells++;
            if (board(i, i) == 'b') {
                blankCol = i;
                blankRow = i;
            }
        }
        if (opponentCells == 2 && blankCol > 0) {
            System.out.println("protectDiagonals, topleft - bottom right");
            return mark(blankCol, blankRow);
        }

        // bottom left to top right
        opponentCells = 0;
        blankCol = 0;
        blankRow = 0;
        for (int col = 1, row = 3; col <= NUM_COL && row > 0; col++, row--) {
            if (System.currentTimeMillis() > finishBy) {
                System.out.println("no time to think! pick one move.");
                return mark(col, row);
            }
            if (board(col, row) == getOpponentRole()) opponentCells++;
            if (board(col, row) == 'b') {
                blankCol = col;
                blankRow = row;
            }
        }
        if (opponentCells == 2 && blankCol > 0 && blankRow > 0) {
            System.out.println("protectDiagonals, bottomleft - top right");
            return mark(blankCol, blankRow);
        }
        return noopMove;
    }

}
