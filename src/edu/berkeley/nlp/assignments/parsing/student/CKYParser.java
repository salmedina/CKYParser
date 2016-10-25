package edu.berkeley.nlp.assignments.parsing.student;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import edu.berkeley.nlp.ling.Tree;
import edu.berkeley.nlp.assignments.parsing.*;
import edu.berkeley.nlp.util.Indexer;


public class CKYParser implements Parser {

    CKYChartElem[][][]      m_Chart;
    SimpleLexicon           m_Lexicon;
    Grammar                 m_Grammar;
    Indexer<String>         m_Indexer;
    UnaryClosure            m_UClosure;
    int                     m_NumTags;
    int                     m_SentLen;
    List<String>            m_Sentence;

    public CKYParser(List<Tree<String>> trainTrees) {
        if(trainTrees.size() < 1) {
            System.out.println("WARNING: Nothing to parse, no trees inserted");
            return;
        }
        System.out.println("Building CKYParser");

        //Binarize trees (Markovization happens here)!
        List<Tree<String>> annotatedTrees = new ArrayList<Tree<String>>();
        for (Tree<String> tree : trainTrees) {
            annotatedTrees.add(TreeAnnotations.annotateTreeLosslessBinarization(tree));
        }

        initialize(annotatedTrees);

        //test();
    }

    public void initialize(List<Tree<String>> trainTrees) {
        m_Lexicon   = new SimpleLexicon(trainTrees);
        m_Grammar   = Grammar.generativeGrammarFromTrees(trainTrees);
        m_Indexer   = m_Grammar.getLabelIndexer();
        m_NumTags   = m_Indexer.size();
        m_UClosure  = new UnaryClosure(m_Indexer, m_Grammar.getUnaryRules());
    }

    public void initChart(int size) {
        int totalCreated = 0;
        m_Chart = new CKYChartElem[m_NumTags][][];
        for (int labelIdx = 0; labelIdx < m_NumTags; labelIdx++) {
            m_Chart[labelIdx] = new CKYChartElem[size][];
            for (int rowIdx = 0; rowIdx < size; rowIdx++) {
                m_Chart[labelIdx][rowIdx] = new CKYChartElem[size-rowIdx];
                for (int colIdx = 0; colIdx < size - rowIdx; colIdx++) {
                    m_Chart[labelIdx][rowIdx][colIdx] = new CKYChartElem();
                    totalCreated++;
                }
            }
        }
        System.out.format("Total cells created [%d][%d][%d] = %d\n",m_NumTags, size, size, totalCreated);
        System.gc();
    }


    public Tree<String> buildJunkTree() {
        return new Tree<String>("ROOT", Collections.singletonList(new Tree<String>("JUNK")));
    }

    public Tree<String> getBestParse(List<String> sentence) {
        // Build chart
        m_Sentence = sentence;
        m_SentLen = m_Sentence.size();
        initChart(m_SentLen);

        // --- INITIALIZE CHART
        // Fill up the diagonal with the terminal values
        for (int tagIdx = 0; tagIdx < m_NumTags; tagIdx++) {
            String tag = m_Indexer.get(tagIdx);
            for (int rowIdx = 0; rowIdx < m_SentLen; rowIdx++) {
                double s = m_Lexicon.scoreTagging(sentence.get(rowIdx), tag);
                s = Double.isNaN(s)? Double.NEGATIVE_INFINITY : s;
                m_Chart[tagIdx][rowIdx][m_SentLen-1-rowIdx].binaryScore = s;

            }
        }

        // --- POPULATE CHART (Forward Pass)
        // This approach might not be the most efficient
        // As it is a brute force approach which traverses through
        // all the possible tags,
        // This code is based on the canonical Viterbi approach
        for (int depth = m_SentLen-1; depth >= 0; --depth) {
            for (int rowIdx = 0; rowIdx <= depth; ++rowIdx) {
                int colIdx = depth-rowIdx;                      //Inverse diagonal
                double baseScore, ruleScore, maxScore;

                // BINARY RULES considering current position as parent
                for (int tagIdx = 0; tagIdx < m_NumTags; ++tagIdx) {
                    maxScore = Double.NEGATIVE_INFINITY;
                    if(depth != m_SentLen-1) {                  //Skip the terminal level
                        int ruleIdx = 0;
                        for (BinaryRule binRule : m_Grammar.getBinaryRulesByParent(tagIdx)) {
                            baseScore = binRule.getScore();
                            for (int step = rowIdx+1; step<m_SentLen-colIdx; ++step) {
                                ruleScore = baseScore;
                                ruleScore += m_Chart[binRule.getLeftChild()][rowIdx][m_SentLen-step].unaryScore;
                                ruleScore += m_Chart[binRule.getRightChild()][step][colIdx].unaryScore;
                                if (ruleScore > maxScore) {
                                    maxScore = ruleScore;
                                    m_Chart[tagIdx][rowIdx][colIdx].binaryRuleIdx = ruleIdx;
                                    m_Chart[tagIdx][rowIdx][colIdx].binaryStep = step;
                                    m_Chart[tagIdx][rowIdx][colIdx].binaryScore = maxScore;
                                }
                            }
                            ruleIdx++;
                        }
                    }
                }

                // Traverse all the unary rules considering current position as parent
                for (int tagIdx = 0; tagIdx < m_NumTags; ++tagIdx) {
                    maxScore = Double.NEGATIVE_INFINITY;
                    boolean foundReflexive = false;
                    for (UnaryRule unaRule : m_UClosure.getClosedUnaryRulesByParent(tagIdx)) {
                        int childIdx = unaRule.getChild();
                        if(childIdx==tagIdx)
                            foundReflexive = true;
                        ruleScore = unaRule.getScore();
                        ruleScore += m_Chart[childIdx][rowIdx][colIdx].binaryScore;
                        if (ruleScore > maxScore) {
                            maxScore = ruleScore;
                            m_Chart[tagIdx][rowIdx][colIdx].unaryChild = unaRule;
                        }
                    }
                    if (foundReflexive) {
                        ruleScore = m_Chart[tagIdx][rowIdx][colIdx].binaryScore;
                        if (ruleScore > maxScore) {
                            maxScore = ruleScore;
                            m_Chart[tagIdx][rowIdx][colIdx].unaryChild = null;
                        }
                    }
                    m_Chart[tagIdx][rowIdx][colIdx].unaryScore = maxScore;
                }
            }
        }

        //printChartValues(0,0);
        //printChartValues(0,1);
        //printChartValues(1,0);

        // --- DECODE THE TREE (Backward Pass)
        Tree<String> ret;
        if (m_Chart[0][0][0].unaryScore == Double.NEGATIVE_INFINITY) {
            ret = new Tree<String>("ROOT", Collections.singletonList(new Tree<String>("JUNK")));
        } else {
            ret = decodeUnaryTreeFrom(0, 0, 0); // Alternates recursively from unary to binary rules
        }

        // --- FORMAT TREE (for output)
        return TreeAnnotations.unAnnotateTree(ret);
    }


    public Tree<String> decodeUnaryTreeFrom(int tagIdx, int rowIdx, int colIdx) {
        Tree<String> resTree;

        UnaryRule rule = m_Chart[tagIdx][rowIdx][colIdx].unaryChild;
        int childTagIdx = (rule == null)? tagIdx : rule.getChild();

        if( rowIdx + colIdx == m_SentLen-1 ) {
            List<Tree<String>> word = Collections.singletonList(new Tree<String>(m_Sentence.get(rowIdx)));
            resTree = new Tree<String>(m_Indexer.get(childTagIdx), word);
        }
        else
        {
            resTree = decodeBinaryTreeFrom(childTagIdx, rowIdx, colIdx);
        }

        if (childTagIdx == tagIdx) {
            return resTree;
        }

        List<Integer> unaryPath = m_UClosure.getPath(rule);
        for (int step = unaryPath.size()-2; step >= 0; --step) {
            int stepTag = unaryPath.get(step);
            resTree = new Tree<String>(m_Indexer.get(stepTag), Collections.singletonList(resTree));
        }
        return resTree;
    }

    public Tree<String> decodeBinaryTreeFrom(int tagIdx, int rowIdx, int colIdx) {
        int ruleNum = m_Chart[tagIdx][rowIdx][colIdx].binaryRuleIdx;                       // Get the index from the rule list
        assert ruleNum != -1 : m_Chart[tagIdx][rowIdx][colIdx].binaryScore;
        int step = m_Chart[tagIdx][rowIdx][colIdx].binaryStep;                                   // Get the traversal path which had best result
        BinaryRule rule = m_Grammar.getBinaryRulesByParent(tagIdx).get(ruleNum);   // extract the rule from the index and label <ROOT = 0>
        ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();   // Build tree for this rule
        children.add(decodeUnaryTreeFrom(rule.getLeftChild(), rowIdx, m_SentLen - step));        // Build the tree for the left child
        children.add(decodeUnaryTreeFrom(rule.getRightChild(), step, colIdx));                // Build the tree for the right child
        return new Tree<String>(m_Indexer.get(tagIdx), children);
    }

    void test() {
        String raw = "FRANKFURT .";
        List<String> sentence = Arrays.asList(raw.split(" "));
        System.out.println(getBestParse(sentence));
        System.exit(-1);
    }

    void printBinaryRule(int parent, int left, int right) {
        System.out.format("%s -> %s %s\n", m_Indexer.get(parent),
                            m_Indexer.get(left), m_Indexer.get(right));
    }

    String printUnaryRule(UnaryRule rule) {
        String ruleStr = String.format("%s -> %s",
                m_Indexer.get(rule.getParent()),
                m_Indexer.get(rule.getChild()));
        return ruleStr;
    }

    void printChartValues(int i, int j) {
        System.out.format("Chart [%d][%d]\n", i, j);

        for (int tagIdx = 0; tagIdx < m_NumTags; ++tagIdx) {
            if( m_Chart[tagIdx][i][j].binaryScore == Double.NEGATIVE_INFINITY &&
                m_Chart[tagIdx][i][j].unaryScore == Double.NEGATIVE_INFINITY)
                continue;

            String unaryRuleStr = "";
            if (m_Chart[tagIdx][i][j].unaryChild != null) {
                unaryRuleStr = printUnaryRule(m_Chart[tagIdx][i][j].unaryChild);
            }
            System.out.format("%12s\t%12.3f\t%12.3f\t%6d\t%6d\t%s\n",
                    m_Indexer.get(tagIdx),
                    m_Chart[tagIdx][i][j].binaryScore,
                    m_Chart[tagIdx][i][j].unaryScore,
                    m_Chart[tagIdx][i][j].binaryRuleIdx,
                    m_Chart[tagIdx][i][j].binaryStep,
                    unaryRuleStr);
        }
        System.out.println();
    }
}
