package org.ggp.base.tournament;

import com.mongodb.client.MongoCollection;
import jskills.GameInfo;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.List;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.elemMatch;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.pullByFilter;
import static com.mongodb.client.model.Updates.push;

/**
 * Created by Amata on 5/30/2016 AD.
 */
public class LeaderBoard {

    public static Document createDefaultRatingUser(String username) {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        return new Document().append("username", username)
            .append("rating", gameInfo.getDefaultRating().getConservativeRating())
            .append("mu", gameInfo.getDefaultRating().getMean())
            .append("sigma", gameInfo.getDefaultRating().getStandardDeviation())
            .append("numMatch", 0)
            .append("win", 0)
            .append("lose", 0)
            .append("draw", 0);
    }

    public static void updateLeaderBoardByUserName(Object leaderBoardID, String username) {

        // if the leader board exists
        List<Document> ranks;
        if (leaderBoardID instanceof ObjectId) {
            // Finds a leader board having this user
            Document leaderBoard = MongoConnection.leaderboards.find(and(eq("_id", leaderBoardID), elemMatch("ranks", eq("username", username)))).first();
            ranks = (List<Document>) leaderBoard.get("ranks");
            Document rankings = new Document("ranks", ranks);

            // removes outdate ranking
            MongoConnection.leaderboards.updateOne(
                    and(eq("_id", leaderBoardID), elemMatch("ranks", eq("username", username))),
                    pullByFilter(elemMatch("ranks", eq("username", username))));

            // adds updated ranking
            MongoConnection.leaderboards.updateOne(
                    eq("_id", leaderBoardID),
                    push("ranks", LeaderBoard.createDefaultRatingUser(username)));

        } else {
            // no leader board, creates new leader board id
            MongoConnection.leaderboards.insertOne(new Document("ranks", LeaderBoard.createDefaultRatingUser(username)));
        }

    }
}
