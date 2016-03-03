package org.ggp.base.player.gamer.statemachine.tictactoe;

import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.util.List;

/**
 * Created by Amata on 3/2/2016 AD.
 */
public abstract class TicTacToePlayer extends BaseGamePlayer {
    private StateMachine theMachine;
    private MachineState theState;
    private Role role;

    private final int NUM_ROW = 3;
    private final int NUM_COL = 3;
    private char[][] board = null;
    private char myRole;
    private char yourRole;
    private boolean myTurn = false;

    private void initBoard() {
        board = new char[NUM_COL][NUM_ROW];
        for (int i = 0; i < NUM_COL; i++)
            for (int j = 0; j < NUM_ROW; j++)
                board[i][j] = 'b';
    }

    public char getMyRole() {
        return myRole;
    }

    private void initMyRole() {
        if (role.toString() == "xplayer") {
            myRole = 'x';
            yourRole = 'o';
        } else {
            myRole = 'o';
            yourRole = 'x';
        }
    }

    private void updateBoard(String state) {
        // updates a board
        // example: ( true ( cell 3 2 b ) )
        int startIndex, row, col;
        startIndex = state.lastIndexOf('l');
        col = Character.getNumericValue(state.charAt(startIndex + 2)) - 1;
        row = Character.getNumericValue(state.charAt(startIndex + 4)) - 1;
        board[col][row] = state.charAt(startIndex + 6);
    }

    private void updateTurn(String state) {
        // updates turn
        // example: ( true ( control xplayer ) )
        if (state.contains("xplayer") && getMyRole() == 'x')
            myTurn = true;
        else
            myTurn = false;
    }

    /*
     * Gets a mark by given a position on a board (row, col)
     * x, o, and b(blank)
     */
    public char board(int col, int row) {
        if (board == null)
            initBoard();
        return board[col - 1][row - 1];
    }

    @Override
    public void init(StateMachine stateMachine, Role theRole) {
        theMachine = stateMachine;
        role = theRole;
        initBoard();
        initMyRole();
    }

    @Override
    public void updateGameState(MachineState machineState) {
        theState = machineState;
        String state;
        for (GdlSentence sentence : machineState.getContents()) {
            state = sentence.toString();

            //System.out.println("state = " + state);
            if (state.contains("cell")) {
                updateBoard(state);
                //System.out.println("updateBoard");
            }
        }
    }

    /**
     * Marks 'x' or 'o' on a board by given row and col.
     * No move return if enter invalid row or col.
     */
    protected Move mark(int col, int row) throws MoveDefinitionException {
        List<Move> moves = theMachine.getLegalMoves(theState, role);
        for (Move move : moves) {
            //move.getContents().toSentence().getName()
            // System.out.println("move = " + move.toString());
            // format:  ( mark col row )
            // example: ( mark 1 3 )
            if (move.toString().charAt(0) == '(' &&
                    col == Character.getNumericValue(move.toString().charAt(7)) &&
                    row == Character.getNumericValue(move.toString().charAt(9))) {

                System.out.println("move = " + move.toString());
                return move;
            }

        }

        return moves.get(0);
    }

}
