package org.ggp.base.player.gamer.statemachine.biddingtictactoe;

import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * Created by Amata on 3/2/2016 AD.
 */
public class MyGamePlayer extends BiddingTicTacToeGame {
    @Override
    Move selectTheMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        if (isBidding()) {
            System.out.println("---------Bidding---------");
            System.out.println("my coins = " + getMyCoins());
            System.out.println("my tiebreaker = " + hasTiebreaker());

            // place a bidding
            if (hasTiebreaker())
                return bid(getMyCoins(), true);
            return bid(getMyCoins(), false);
        } else {
            System.out.println("---------Marking---------");
            // mark on a board
            if (board(1, 3) == 'b')
                return mark(1, 3);
            return mark(1, 3);
        }
    }
}
