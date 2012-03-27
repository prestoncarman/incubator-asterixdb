package edu.uci.ics.asterix.runtime.evaluators.common;

import edu.uci.ics.asterix.om.types.ATypeTag;
import edu.uci.ics.fuzzyjoin.similarity.SimilarityMetricJaccard;
import edu.uci.ics.hyracks.algebricks.core.algebra.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.algebricks.core.api.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.IDataOutputProvider;

// Assumes that both arguments are sorted by the same ordering.
public class SimilarityJaccardSortedEvaluator extends SimilarityJaccardEvaluator {

    protected final SimilarityMetricJaccard jaccard = new SimilarityMetricJaccard();
    
	public SimilarityJaccardSortedEvaluator(IEvaluatorFactory[] args,
			IDataOutputProvider output) throws AlgebricksException {
		super(args, output);
	}

    protected float computeResult(byte[] bytes, int firstStart, int secondStart, ATypeTag argType)
            throws AlgebricksException {
        return jaccard.getSimilarity(firstListIter, secondListIter);
    }
}