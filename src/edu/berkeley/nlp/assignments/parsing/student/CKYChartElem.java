package edu.berkeley.nlp.assignments.parsing.student;

import edu.berkeley.nlp.assignments.parsing.BinaryRule;
import edu.berkeley.nlp.assignments.parsing.UnaryRule;

/**
 * Created by zal on 10/23/16.
 */
public class CKYChartElem {
    double      binaryScore;
    double      unaryScore;
    int         binaryRuleIdx;
    int         binaryStep;
    UnaryRule   unaryChild;

    public CKYChartElem() {
        binaryScore     = Double.NEGATIVE_INFINITY;
        unaryScore      = Double.NEGATIVE_INFINITY;
        binaryRuleIdx   = -1;
        binaryStep      = -1;
        unaryChild      = null;
    }
}
