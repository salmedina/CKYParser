package edu.berkeley.nlp.assignments.parsing.student;

/**
 * Created by zal on 10/23/16.
 */
public class CKYPos {
    public int row;
    public int col;

    public CKYPos() {
        this.row = -1;
        this.col = -1;
    }

    public CKYPos(int row, int col) {
        this.set(row, col);
    }

    public void set(int row, int col) {
        this.row = row;
        this.col = col;
    }

    public String toString() {
        return String.format("[%d, %d]",this.row, this.col);
    }
}
