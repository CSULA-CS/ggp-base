package org.ggp.base.player.gamer.statemachine.biddingtictactoe;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.player.gamer.statemachine.biddingtictactoe.BiddingTicTacToeGamer;

import java.util.List;

public final class MyBiddingTicTacToeGamer extends BiddingTicTacToeGamer {
    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        StateMachine theMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> moves = theMachine.getLegalMoves(getCurrentState(), getRole());
        updateGameState();

        Move selection = makeMyMove();

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }
}
