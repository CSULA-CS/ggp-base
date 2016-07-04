package tictactoe;

public class TicTacToeLogic {

    public TicTacToeMove getMyMove(char[][] board, char myRole, long timeout) {
        int DIMENSION = 3; // 3 by 3 board.
        char BLANK = 'b';
        long finishBy = timeout - 1000; // timeout: a point of time in future in miliseconds.

        // finds first blank in a column
        // 'board' is a 2D array of character.
        // Variable myRole is either 'x' or 'o' and BLANK is 'b'.
        for (int col = 0; col < DIMENSION; col++) {
            for (int row = 0; row < DIMENSION; row++) {
                if (board[col][row] == BLANK) return new TicTacToeMove(col, row);
            }
        }

        // if board[0][0] is not blank, it will return random blank cell.
        return new TicTacToeMove(0, 0);
    }
}
