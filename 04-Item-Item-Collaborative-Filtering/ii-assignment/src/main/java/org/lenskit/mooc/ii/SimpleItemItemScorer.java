package org.lenskit.mooc.ii;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemScorer extends AbstractItemScorer {
    private final SimpleItemItemModel model;
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    @Inject
    public SimpleItemItemScorer(SimpleItemItemModel m, DataAccessObject dao) {
        model = m;
        this.dao = dao;
        neighborhoodSize = 20;
    }

    /**
     * Score items for a user.
     * @param user The user ID.
     * @param items The score vector.  Its key domain is the items to score, and the scores
     *               (rating predictions) should be written back to this vector.
     */
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        Long2DoubleMap itemMeans = model.getItemMeans();
        Long2DoubleMap ratings = getUserRatingVector(user);

        // TODO Normalize the user's ratings by subtracting the item mean from each one.

        for(Map.Entry<Long,Double> rating: ratings.entrySet()) {
            rating.setValue(rating.getValue()-itemMeans.get(rating.getKey()));
        }

        // TODO Compute the user's score for each item, add it to results

        List<Result> results = new ArrayList<>();

        for (long item: items ) {

            double meanRating = itemMeans.get(item);
            Long2DoubleMap neighbors = model.getNeighbors(item);

            double numerator = 0.0;
            double denominator = 0.0;
            int numOfNeighbors = 0;

            List<Map.Entry<Long,Double>> closeItems = new ArrayList<>(neighbors.entrySet());
            Collections.sort(closeItems, (o1, o2) -> o2.getValue().compareTo(o1.getValue()));

            for(Map.Entry<Long,Double> closeItem: closeItems) {

                if (numOfNeighbors >= 20) { break; }

                if (ratings.containsKey(closeItem.getKey())) {
                    numOfNeighbors++;
                    numerator += closeItem.getValue()*(ratings.get(closeItem.getKey()));
                    denominator += closeItem.getValue();
                }
            }

            results.add(Results.create(item,meanRating + (numerator/denominator)));

        }

        return Results.newResultMap(results);

    }

    /**
     * Get a user's ratings.
     * @param user The user ID.
     * @return The ratings to retrieve.
     */
    private Long2DoubleOpenHashMap getUserRatingVector(long user) {
        List<Rating> history = dao.query(Rating.class)
                                  .withAttribute(CommonAttributes.USER_ID, user)
                                  .get();

        Long2DoubleOpenHashMap ratings = new Long2DoubleOpenHashMap();
        for (Rating r: history) {
            ratings.put(r.getItemId(), r.getValue());
        }

        return ratings;
    }


}
