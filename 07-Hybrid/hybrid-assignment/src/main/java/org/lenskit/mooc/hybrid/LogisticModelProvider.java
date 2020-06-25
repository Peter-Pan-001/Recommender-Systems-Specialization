package org.lenskit.mooc.hybrid;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.lenskit.api.ItemScorer;
import org.lenskit.api.Result;
import org.lenskit.bias.BiasModel;
import org.lenskit.bias.UserBiasModel;
import org.lenskit.data.ratings.Rating;
import org.lenskit.data.ratings.RatingSummary;
import org.lenskit.inject.Transient;
import org.lenskit.util.ProgressLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Trainer that builds logistic models.
 */
public class LogisticModelProvider implements Provider<LogisticModel> {
    private static final Logger logger = LoggerFactory.getLogger(LogisticModelProvider.class);
    private static final double LEARNING_RATE = 0.00005;
    private static final int ITERATION_COUNT = 100;

    private final LogisticTrainingSplit dataSplit;
    private final BiasModel baseline;
    private final RecommenderList recommenders;
    private final RatingSummary ratingSummary;
    private final int parameterCount;
    private final Random random;

    @Inject
    public LogisticModelProvider(@Transient LogisticTrainingSplit split,
                                 @Transient UserBiasModel bias,
                                 @Transient RecommenderList recs,
                                 @Transient RatingSummary rs,
                                 @Transient Random rng) {
        dataSplit = split;
        baseline = bias;
        recommenders = recs;
        ratingSummary = rs;
        parameterCount = 1 + recommenders.getRecommenderCount() + 1;
        random = rng;
    }

    @Override
    public LogisticModel get() {
        List<ItemScorer> scorers = recommenders.getItemScorers();
        double intercept = 0;
        double[] params = new double[parameterCount];

        LogisticModel current = LogisticModel.create(intercept, params);

        // TODO Implement model training

        List<Rating> tune_ratings = dataSplit.getTuneRatings();

        for (int iteration=0; iteration < ITERATION_COUNT; iteration++){
            Collections.shuffle(tune_ratings);

            for (Rating r: tune_ratings) {
                long itemId = r.getItemId();
                long userId = r.getUserId();
                double b_ui = baseline.getIntercept() + baseline.getUserBias(userId) + baseline.getItemBias(itemId);
                double lg_popularity = Math.log(ratingSummary.getItemRatingCount(itemId));

                RealVector x_array = new ArrayRealVector(parameterCount);

                double y = r.getValue();

                x_array.setEntry(0, b_ui);
                x_array.setEntry(1, lg_popularity);
                int i = 2;
                for (ItemScorer scorer : scorers) {
                    Result score_result = scorer.score(userId, itemId);
                    if (score_result == null) {
                        x_array.setEntry(i, 0.);
                        i += 1;
                        continue;
                    }
                    double x_value = score_result.getScore() - b_ui;
                    x_array.setEntry(i, x_value);
                    i += 1;
                }

                double sigmoid = current.evaluate(-y, x_array);

                intercept += LEARNING_RATE * y * sigmoid;
                for (int param_iteration = 0; param_iteration < parameterCount; param_iteration++) {
                    params[param_iteration] += LEARNING_RATE * y * x_array.getEntry(param_iteration) * sigmoid;
                }

                current = LogisticModel.create(intercept, params);
            }
        }
        return current;
    }

}
