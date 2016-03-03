package org.ggp.base.player.gamer.statemachine.biddingtictactoe;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

/**
 * Created by Amata on 3/2/2016 AD.
 */
public abstract class BaseGamePlayer {

    abstract void init(StateMachine stateMachine, Role theRole);

    abstract void updateGameState(MachineState machineState);

    abstract Move selectTheMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException;

}
