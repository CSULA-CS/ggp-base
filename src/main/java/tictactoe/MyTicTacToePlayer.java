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

        Move move = noopMove;
        for (int col = 1; col <= this.NUM_COL; col++) {
            for (int row = 1; row <= this.NUM_ROW; row++) {
                // not enough time to decide a move, return 'noop'
                if (System.currentTimeMillis() > finishBy) {
                    System.out.println("no time to think! pick one move.");
                    return mark(col, row);
                }

                if (board(col, row) == 'b')
                    return mark(col, row);
            }
        }

        return move;
    }

    // function to try to win
}
