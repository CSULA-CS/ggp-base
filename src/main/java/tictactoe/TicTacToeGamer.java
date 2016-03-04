package tictactoe;

import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.util.List;


public class TicTacToeGamer extends SampleGamer {
    private BaseGamePlayer thePlayer;

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        thePlayer = new MyTicTacToePlayer(); // Specifies the class defining logic by user
        thePlayer.init(getStateMachine(), getRole());

    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        StateMachine theMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> moves = theMachine.getLegalMoves(getCurrentState(), getRole());
        if (moves.size() == 1) {
            // if not my turn, return 'noop'.
            thePlayer.updateGameState(getCurrentState());
            Move selection = moves.get(0);
            long stop = System.currentTimeMillis();
            notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
            return selection;
        }

        // My turn
        thePlayer.updateGameState(getCurrentState());
        Move selection = thePlayer.selectTheMove(timeout);
        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }
}
