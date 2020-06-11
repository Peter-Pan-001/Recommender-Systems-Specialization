package org.lenskit.mooc.uu;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleSortedMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.entities.CommonTypes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.math.Scalars;
import org.lenskit.util.math.Vectors;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    /**
     * Instantiate a new user-user item scorer.
     * @param dao The data access object.
     */
    @Inject
    public SimpleUserUserItemScorer(DataAccessObject dao) {
        this.dao = dao;
        neighborhoodSize = 30;
    }

    @Nonnull
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        // TODO Score the items for the user with user-user CF
        List<Result> results = new ArrayList<>();

        LongSet userIDs = dao.getEntityIds(CommonTypes.USER);

        Map<Long,Long2DoubleOpenHashMap> userRatings = new HashMap<>();

        // Get normalized ratings for each user (minus each user's average rating)
        for (long userID: userIDs) {

            Long2DoubleOpenHashMap ratings = getUserRatingVector(userID);

            double mean = Vectors.mean(ratings);

            for(Map.Entry<Long,Double> rating : ratings.entrySet()) {
                rating.setValue(rating.getValue() - mean);
            }
            userRatings.put(userID, ratings);
        }

        // Calculate Cosine Similarity
        Map<Long,Double> cosSim = new HashMap<>();
        for (Long userID: userIDs) {

            // userID is a iterated user, user is the target user.
            if (userID == user) {
                continue;
            }

            double cos = (Vectors.dotProduct(userRatings.get(user), userRatings.get(userID)))/
                    (Vectors.euclideanNorm(userRatings.get(user)) * Vectors.euclideanNorm(userRatings.get(userID)));

            // Ignore negative correlation
            if (cos > 0.0) {
                cosSim.put(userID, cos);
            }
        }

        //Sort DESC
        List<Map.Entry<Long,Double>> cosSimList = new ArrayList<>(cosSim.entrySet());
        Collections.sort(cosSimList, new Comparator<Map.Entry<Long, Double>>() {
            @Override
            public int compare(Map.Entry<Long, Double> o1, Map.Entry<Long, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        // Iterate items to compute predicted rating
        for(Long item: items) {

            double numerator = 0.0;
            double denominator = 0.0;
            int numOfNeighborhood = 0;

            // Iterate each {userID:cos}
            for(Map.Entry<Long,Double> entry : cosSimList) {

                // Include at most 30 nearest neighbors
                if (numOfNeighborhood >= 30) {
                    break;
                }

                long userID = entry.getKey();
                if(userRatings.get(userID).containsKey(item)) {
                    numOfNeighborhood++;
                    numerator += entry.getValue() * (userRatings.get(userID).get(item));
                    denominator += entry.getValue();
                }
            }

            // denominator != 0 and numOfNeighborhood >=2
            if(denominator == 0 || numOfNeighborhood < 2) {
                results.add(Results.create(item,0.0));
            }else {
                results.add(Results.create(
                        item, (Vectors.mean(getUserRatingVector(user)) + (numerator/denominator)))
                );
            }
        }

        return Results.newResultMap(results);
    }

    /**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector, mapping item IDs to the user's rating
     *         for that item.
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
