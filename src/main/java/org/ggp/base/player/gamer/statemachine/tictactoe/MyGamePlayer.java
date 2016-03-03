package org.ggp.base.player.gamer.statemachine.tictactoe;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.util.Random;

/**
 * Created by Amata on 3/2/2016 AD.
 */
public class MyGamePlayer extends NewTicTacToePlayer {
    
    /*
     *
     * char board(int col, int row)
     * Returns char at a position (column, row) on the grid.
     * 'b' = blank, 'x' and 'o'.
     *
     * char getMyRole()
     * Returns a role 'x' or 'o'.
     *
     * boolean isMyTurn()
     * Returns true, if it's a turn. Otherwise, returns false.
     *
     * Move mark(int col, int row)
     * Marks 'x' or 'o' on the grid and returns Move object required by GGP.
     * The Move object will be translated into GDL term.
     *
     */

    @Override
    Move selectTheMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        int numCol = 3;
        int numRow = 3;

        if (isMyTurn()) {
            if (board(1, 3) == 'b')
                return mark(1, 3);

            for (int col = 1; col <= numCol; col++) {
                for (int row = 1; row <= numRow; row++) {
                    if (board(col, row) == 'b')
                        return mark(col, row);
                }
            }
            return mark(numCol, numRow);
        }

        return mark(0, 0);
    }
}
