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
    private TicTacToeStateTransformer transformer;
    private TicTacToeLogic logic;

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        transformer = new TicTacToeStateTransformer(getStateMachine(), getRole());
        logic = new TicTacToeLogic();
    }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        StateMachine theMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> moves = theMachine.getLegalMoves(getCurrentState(), getRole());
        transformer.updateGameState(getCurrentState());

        if (moves.size() == 1) {
            // if this is not my turn, return 'noop'.
            Move selection = moves.get(0);
            long stop = System.currentTimeMillis();
            notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
            return selection;
        }

        // My turn
        TicTacToeMove myMove = logic.getMyMove(transformer.getBoard(), transformer.getMyRole(), timeout);
        Move selection = transformer.createMove(myMove);
        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }
}
