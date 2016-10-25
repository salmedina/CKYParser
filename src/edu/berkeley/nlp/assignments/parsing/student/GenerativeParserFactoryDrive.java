package edu.berkeley.nlp.assignments.parsing.student;

import java.lang.Integer;
import java.util.*;
import java.util.ArrayList;

import edu.berkeley.nlp.util.Indexer;
import edu.berkeley.nlp.assignments.parsing.*;
import edu.berkeley.nlp.ling.Tree;


public class GenerativeParserFactoryDrive implements ParserFactory {

    public Parser getParser(List<Tree<String>> trainTrees) {
        return new GenerativeParserDrive(trainTrees);
    }

}

class GenerativeParserDrive implements Parser {
    SimpleLexicon lexicon;
    Grammar grammar;
    Indexer<String> indexer;
    UnaryClosure unaryClosure;
    List<String> currentSentence;
    int numLabels;
    int sentenceLength;
    boolean TEST = false;

    GenerativeParserDrive(List<Tree<String>> trainTrees) {
        ArrayList<Tree<String>> trees = new ArrayList<Tree<String>>();
        for (Tree<String> tree : trainTrees) {
            //Tree<String> newTree = FineAnnotator.annotateTree(tree);
            //trees.add(newTree);
        }
        assert trees.size() > 0 : "No training trees";
        lexicon = new SimpleLexicon(trees);
        grammar = Grammar.generativeGrammarFromTrees(trees);
        indexer = grammar.getLabelIndexer();
        unaryClosure = new UnaryClosure(indexer, grammar.getUnaryRules());
        numLabels = indexer.size();

        if (TEST) {
            test();
            System.exit(0);
        }
    }

    double [][][] binaryScores;         // Stores the scores for the binary rules of this chart pos
    double [][][] unaryScores;          // Stores the scores for the unary rules of this chart pos
    int [][][] binaryRuleNum;           // Stores the index of the rule with highest probability
    int [][][] binaryK;                 // Stores the index of the position traversal while looking for rules
    UnaryRule [][][] unaryChild;        // Stores the next symbol from current position

    void initTables(int size) {
        binaryScores        = new double[numLabels][][];
        unaryScores         = new double[numLabels][][];
        binaryRuleNum       = new int[numLabels][][];
        binaryK             = new int[numLabels][][];
        unaryChild          = new UnaryRule[numLabels][][];
        initTable(unaryScores, size);
        initTable(binaryScores, size);
        initTable(binaryRuleNum, size);
        initTable(binaryK, size);
        initTable(unaryChild, size);
    }

    void initTable(double[][][] table, int size) {
        for (int x = 0; x < numLabels; x++) {
            double [][] page = new double[size][];
            table[x] = page;
            for (int i = 0; i < size; i++) {
                page[i] = new double[size - i];
                Arrays.fill(page[i], Double.NaN);
            }
        }
    }

    void initTable(int[][][] table, int size) {
        for (int x = 0; x < numLabels; x++) {
            int [][] page = new int[size][];
            table[x] = page;
            for (int i = 0; i < size; i++) {
                page[i] = new int[size - i];
                Arrays.fill(page[i], -1);
            }
        }
    }

    void initTable(UnaryRule[][][] table, int size) {
        for (int x = 0; x < numLabels; x++) {
            UnaryRule [][] page = new UnaryRule[size][];
            table[x] = page;
            for (int i = 0; i < size; i++) {
                page[i] = new UnaryRule[size - i];
            }
        }
    }

    public Tree<String> getBestParse(List<String> sentence) {
        // -- Initialize diagonal
        currentSentence = sentence;
        sentenceLength = sentence.size();
        initTables(sentenceLength);

        // Fill binaryScores's diagonal with Lexicon tag Scores
        for (int x = 0; x < numLabels; x++) {
            String transformedLabel = indexer.get(x);
            double [][] labelTable = binaryScores[x];
            for (int j = 0; j < sentenceLength; j++) {
                double s = lexicon.scoreTagging(sentence.get(j), transformedLabel);
                if (Double.isNaN(s)) s = Double.NEGATIVE_INFINITY;

                labelTable[j][sentenceLength -j-1] = s;
            }
        }

        // -- FORWARD PASS
        for (int sum = sentenceLength - 1; sum >= 0; sum--) {   // For each level (sum is the level)
            for (int i = 0; i <= sum; i++) {            // This is a diagonal traversal base on the row (i)
                int j = sum - i;                        // j (col) is based on the row and the current level
                double s, ruleScore, max;

                // Infer Binary rules from Unary rules
                for (int x = 0; x < numLabels; x++) {   // for each possible label
                    max = Double.NEGATIVE_INFINITY;     // scores are given from -INF to 0
                    if (sum != sentenceLength - 1) {            // We do not do this for the first level, since it doesn't have children
                        int ruleNum = 0;
                        for (BinaryRule rule : grammar.getBinaryRulesByParent(x)) { //For each of the binary rules that have this as parent
                            ruleScore = rule.getScore();                            //get the rule score
                            assert sentenceLength - j > i + 1;
                            for (int k = i + 1; k < sentenceLength - j; k++) {              // K is the traversal position for matching
                                s = ruleScore;                                      // copy the current score
                                assert ruleScore <= 0;
                                s += unaryScores[rule.getLeftChild()][i][sentenceLength - k];
                                s += unaryScores[rule.getRightChild()][k][j];       // Sum scores to get rule score
                                if (s > max) {                                      // Keep the max rule by iteration index and rule index in array
                                    max = s;
                                    binaryRuleNum[x][i][j] = ruleNum;
                                    binaryK[x][i][j] = k;
                                }
                            }
                            ruleNum++;
                        }
                        assert max == Double.NEGATIVE_INFINITY || binaryRuleNum[x][i][j] != -1;
                        binaryScores[x][i][j] = max;                // Keep the max score for the corresponding label
                    }
                }

                for (int x = 0; x < numLabels; x++) {               // For each of the labels in the current position (i,j)
                    max = Double.NEGATIVE_INFINITY;                 // Temporal vars: if unaryrule calls itself and max score
                    boolean selfLooped = false;
                    for (UnaryRule rule : unaryClosure.getClosedUnaryRulesByParent(x)) {    //For each rule that has as parent X
                        int child = rule.getChild();                // get the child
                        if (child == x) selfLooped = true;          // check if its reflexive
                        s = rule.getScore();                        // get the score of the rule
                        s += binaryScores[child][i][j];             // add it to the corresponding score of the binary rule
                        if (s > max) {                              // if it is the maximum, store the rule
                            max = s;
                            unaryChild[x][i][j] = rule;
                        }
                    }
                    if (!selfLooped) {                              // Only if it is not a reflexive rule
                        s = binaryScores[x][i][j];                  // Get the corresponding binary rule score
                        if (s > max) {                              // and if it is better than the unary rule score
                            max = s;
                            unaryChild[x][i][j] = null;             // remove the unary rule score
                        }
                    }
                    unaryScores[x][i][j] = max;                     // Store the maximum score of the selected unary rule
                }
            }
        }

        // --- DECODE
        Tree<String> ret;
        if (unaryScores[0][0][0] == Double.NEGATIVE_INFINITY) {
            ret = new Tree<String>("ROOT", Collections.singletonList(new Tree<String>("JUNK")));
        } else {
            ret = unaryTree(0, 0, 0);
        }

        return TreeAnnotations.unAnnotateTree(ret);
    }

    Tree<String> unaryTree(int x, int i, int j) {                   // DECODE THE TREE FROM UNARY RULES
        UnaryRule rule = unaryChild[x][i][j];                       // Get the unary rule from this position
        int child = rule == null ? x : rule.getChild();             // if no rule, set child as the current label
        Tree<String> tree;

        if (i + j == sentenceLength - 1) {                                  // If reached terminal node (STOP CONDITION RECURSIVENESS
            List<Tree<String>> word = Collections.singletonList(new Tree<String>(currentSentence.get(i)));  // Get the word tree
            tree = new Tree<String>(indexer.get(child), word);      // Append word to tree
        } else {
            tree = binaryTree(child, i, j);                         // If non-terminal, go binary, open gate until reach non-terminal RECURSIVE CALL
        }

        if (child == x) return tree;                                // if it is reflexive return the tree

        List<Integer> path = unaryClosure.getPath(rule);            //  Get the closure for the current rule
        assert path.get(path.size() - 1) == child;
        for (int k = path.size() - 2; k >= 0; k--) {                //  For each element in the rule expansion
            int tag = path.get(k);
            tree = new Tree<String>(indexer.get(tag), Collections.singletonList(tree)); // Add each one of those to the tree
        }
        assert path.get(0) == x;
        return tree;                                                // return the tree
    }

    Tree<String> binaryTree(int x, int i, int j) {                  // DECODE THE TREE FROM BINARY RULES
        int ruleNum = binaryRuleNum[x][i][j];                       // Get the index from the rule list
        assert ruleNum != -1 : binaryScores[x][i][j];
        int k = binaryK[x][i][j];                                   // Get the traversal path which had best result
        BinaryRule rule = grammar.getBinaryRulesByParent(x).get(ruleNum);   // extract the rule from the index and label <ROOT = 0>
        ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();   // Build tree for this rule
        children.add(unaryTree(rule.getLeftChild(), i, sentenceLength - k));        // Build the tree for the left child
        children.add(unaryTree(rule.getRightChild(), k, j));                // Build the tree for the right child
        return new Tree<String>(indexer.get(x), children);                  // Return the tree
    }

    void test() {
        String raw = "Odds and Ends";
        List<String> sentence = Arrays.asList(raw.split(" "));
        System.out.println(getBestParse(sentence));
    }

    void printArray(double[][] arr) {
        System.out.println(Arrays.deepToString(arr));
    }
    void printArray(double[][][] arr) {
        System.out.println(Arrays.deepToString(arr));
    }
}


