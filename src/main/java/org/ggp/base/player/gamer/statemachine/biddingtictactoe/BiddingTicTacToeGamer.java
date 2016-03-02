package org.ggp.base.player.gamer.statemachine.biddingtictactoe;


import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.util.List;

/**
 * Starter class for Bidding Tic Tac Toe player
 */
public abstract class BiddingTicTacToeGamer extends SampleGamer {
    private final int NUM_ROW = 3;
    private final int NUM_COL = 3;
    private char[][] board = null;
    private boolean tiebreaker = false;
    private boolean bidding = true;
    private int myCoins = 0;
    private int yourCoins = 0;
    private char myRole;
    private char yourRole;

    protected void initBoard() {
        System.out.println("initBoard");
        board = new char[NUM_COL][NUM_ROW];
        for (int i = 0; i < NUM_COL; i++)
            for (int j = 0; j < NUM_ROW; j++)
                board[i][j] = 'b';
    }

    protected char getMyRole() {
        System.out.println("getMyRole = " + getRole().toString());
        if (getRole().toString() == "x")
            return 'x';
        return 'o';
    }

    protected void initMyRole() {
        System.out.println("initMyRole");
        if (getRole().toString() == "x") {
            myRole = 'x';
            yourRole = 'o';
        } else {
            myRole = 'o';
            yourRole = 'x';
        }
    }

    public boolean isBidding() {
        return bidding;
    }

    public int getMyCoins() {
        return myCoins;
    }

    public int getYourCoins() {
        return yourCoins;
    }

    public boolean hasTiebreaker() {
        return tiebreaker;
    }

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        initBoard();
        initMyRole();
        System.out.println("my role = " + getMyRole());
    }

    /**
     * Updates a board so that develops can call "board[row][col]" to have a current board.
     * Also a number of coins and tiebreaker.
     */
    protected void updateGameState() {

        String state;
        for (GdlSentence sentence : getCurrentState().getContents()) {
            state = sentence.toString();
            //System.out.println("state = " + state);
            if (state.contains("cell")) {
                updateBoard(state);
                //System.out.println("updateBoard");
            } else if (state.contains("coins")) {
                updateCoins(state);
                //System.out.println("updateCoins");
            } else if (state.contains("tiebreaker")) {
                updateTiebreaker(state);
                // System.out.println("updateTiebreaker");
            } else if (state.contains("bidding")) {
                bidding = true;
                // System.out.println("bidding");
            } else if (state.contains("control")) {
                bidding = false;
                // System.out.println("control");
                // System.out.println("state = " + state);

            }
        }
    }

    private void updateBoard(String state) {
        // updates a board
        // example: ( true ( cell 3 2 b ) )
        int startIndex, row, col;
        startIndex = state.lastIndexOf('l');
        row = Character.getNumericValue(state.charAt(startIndex + 2)) - 1;
        col = Character.getNumericValue(state.charAt(startIndex + 4)) - 1;
        board[col][row] = state.charAt(startIndex + 6);
    }

    private void updateTiebreaker(String state) {
        // updates tiebreaker
        // example: ( true ( tiebreaker x ) )
        if ((state.contains("x") && getMyRole() == 'x') || (state.contains("o") && getMyRole() == 'o'))
            tiebreaker = true;
        else
            tiebreaker = false;
    }

    private void updateCoins(String state) {
        // updates coins
        // example: ( true ( coins x 6 ) )
        int len = "coins x ".length();
        if (state.contains("x") && myRole == 'x') {
            myCoins = Character.getNumericValue(state.charAt(state.indexOf("coins x ") + len));
        } else if (state.contains("x") && myRole == 'o') {
            yourCoins = Character.getNumericValue(state.charAt(state.indexOf("coins x ") + len));
        } else if (state.contains("o") && myRole == 'x') {
            yourCoins = Character.getNumericValue(state.charAt(state.indexOf("coins o ") + len));
        } else if (state.contains("o") && myRole == 'o') {
            myCoins = Character.getNumericValue(state.charAt(state.indexOf("coins o ") + len));
        }
    }

    /*
     * Gets a mark by given a position on a board (row, col)
     * x, o, and b(blank)
     */
    protected char board(int col, int row) {
        if (board == null)
            initBoard();
        return board[col - 1][row - 1];
    }

    protected Move bid(int coins, boolean useTiebreaker) throws MoveDefinitionException {
        if (coins > myCoins)
            coins = myCoins;
        else if (coins < 0)
            coins = 0;

        List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
        for (Move move : moves) {
            //( bid 2 with_tiebreaker )
            //( bid 2 no_tiebreaker )
            System.out.println("bid = " + move.toString());
            String moveString = move.toString();
            int coinsOfThisMove = Character.getNumericValue(move.toString().charAt(moveString.indexOf("bid") + "bid ".length()));
            System.out.println("coinsOfThisMove = " + coinsOfThisMove);
            if (tiebreaker && useTiebreaker && moveString.contains("with_tiebreaker") && coinsOfThisMove == coins) {
                System.out.println("move = " + move.toString());
                return move;
            } else if (tiebreaker && !useTiebreaker && moveString.contains("no_tiebreaker") && coinsOfThisMove == coins) {
                System.out.println("move = " + move.toString());
                return move;
            } else if (!tiebreaker && moveString.contains("no_tiebreaker") && coinsOfThisMove == coins) {
                System.out.println("move = " + move.toString());
                return move;
            }
        }

        System.out.println("bid invalid, pick first bid instead");
        return moves.get(0);
    }

    /**
     * Marks 'x' or 'o' on a board by given row and col.
     * No move return if enter invalid row or col.
     */
    protected Move mark(int row, int col) throws MoveDefinitionException {
        List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
        for (Move move : moves) {
            //move.getContents().toSentence().getName()
            //System.out.println("move = " + move.toString());
            // format:  ( mark col row )
            // example: ( mark 1 3 )
            if (move.toString().charAt(0) == '(' &&
                    row == Character.getNumericValue(move.toString().charAt(7)) &&
                    col == Character.getNumericValue(move.toString().charAt(9)))
                return move;
        }

        System.out.println("move invalid, pick first move instead");
        return moves.get(0);
    }

    /*@Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        StateMachine theMachine = getStateMachine();
        long start = System.currentTimeMillis();
        long finishBy = timeout - 1000;

        List<Move> moves = theMachine.getLegalMoves(getCurrentState(), getRole());
        updateGameState();

        Move selection = makeMyMove();

        long stop = System.currentTimeMillis();
        notifyObservers(new GamerSelectedMoveEvent(moves, selection, stop - start));
        return selection;
    }*/

    // ** constructMyMove(), decideMyMove, selectMove
    public Move makeMyMove() throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

        if (isBidding()) {
            System.out.println("---------Bidding---------");
            System.out.println("my coins = " + myCoins);
            System.out.println("my tiebreaker = " + tiebreaker);

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

