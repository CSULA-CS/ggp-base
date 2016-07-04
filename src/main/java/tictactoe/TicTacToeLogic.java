package tictactoe;

public class TicTacToeLogic {

    public TicTacToeMove getMyMove(char[][] board, char myRole, long timeout) {
        int DIM = 3;
        char BLANK = 'b';
        long finishBy = timeout - 1000;

        // finds first blank in a column
        for (int col = 0; col < DIM; col++) {
            for (int row = 0; row < DIM; row++) {
                if (board[col][row] == BLANK) return new TicTacToeMove(col, row);
            }
        }

        // if board[0][0] is not blank, it will return random blank cell.
        return new TicTacToeMove(0, 0);
    }
}
