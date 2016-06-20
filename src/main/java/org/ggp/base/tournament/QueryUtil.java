package org.ggp.base.tournament;

import clojure.lang.Obj;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.result.UpdateResult;
import jskills.GameInfo;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.print.Doc;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.ascending;
import static com.mongodb.client.model.Sorts.descending;
import static com.mongodb.client.model.Updates.*;

/**
 * Created by Amata on 5/30/2016 AD.
 */
public class QueryUtil {

    private static GameInfo gameInfo = GameInfo.getDefaultGameInfo();

    public static void setPlayerReady(ObjectId playerID, String pathToClasses) {
        UpdateResult result =
            MongoConnection.players.updateOne(
                eq("_id", playerID),
                new Document("$set", new Document("pathToClasses", pathToClasses)
                    .append("status", "ready")
                    .append("srcStatus", "compiled")
                    .append("rating", gameInfo.getDefaultRating().getConservativeRating())
                    .append("mu", gameInfo.getDefaultRating().getMean())
                    .append("sigma", gameInfo.getDefaultRating().getStandardDeviation())
                    .append("numMatch", 0)
                    .append("win", 0)
                    .append("lose", 0)
                    .append("draw", 0)
                    .append("createdAt", System.currentTimeMillis())));
    }

    public static Document getPlayer(ObjectId playerID) {
        return MongoConnection.players.find(eq("_id", playerID)).first();
    }

    public static Document getPlayer(ObjectId tourID, String username) {
        return MongoConnection.players.find(and(eq("tournament_id", tourID), eq("username", username))).first();
    }

    public static List<Document> getAllPlayers() {
        return MongoConnection.players.find().into(new ArrayList<Document>());
    }

    public static List<Document> getPlayers(ObjectId tourID) {
        return MongoConnection.players.find(eq("tournament_id", tourID)).into(new ArrayList<Document>());
    }

    public static List<Document> getPlayers(ObjectId tourID, List<String> users) {
        return MongoConnection.players.find(and(eq("tournament_id", tourID), in("username", users))).into(new ArrayList<Document>());
    }

    public static void updatePlayerSkill(Document player) {
        MongoConnection.players.findOneAndUpdate(
            eq("_id", player.getObjectId("_id")),
            and(set("status", "ready"),
                set("rating", player.getDouble("rating").doubleValue()),
                set("mu", player.getDouble("mu").doubleValue()),
                set("sigma", player.getDouble("sigma").doubleValue()),
                set("win", player.getInteger("win").intValue()),
                set("lose", player.getInteger("lose").intValue()),
                set("draw", player.getInteger("draw").intValue()),
                set("numMatch", player.getInteger("numMatch").intValue())
            ));
    }

    public static void updatePlayerStatus(ObjectId playerID, String status) {
        MongoConnection.players.findOneAndUpdate(
            eq("_id", playerID),
            set("status", status));
    }

    public static List<Document> getMatches(ObjectId tourID) {
        return MongoConnection.matches.find(eq("tournament_id", tourID)).into(new ArrayList<Document>());
    }

    public static List<Document> getMatches(ObjectId tourID, long start) {
        return MongoConnection.matches.find(
            and(
                eq("tournament_id", tourID),
                gt("createdAt", start))).into(new ArrayList<Document>());
    }

    public static List<Document> getNumMatches(ObjectId tourID, String username) {
        return MongoConnection.matches.find(
            and(
                eq("tournament_id", tourID),
                Filters.elemMatch("result", eq("username", username))
            )).into(new ArrayList<Document>());
    }

    public static List<Document> getMatchesWithUserScore(ObjectId tourID, String username, int score) {
        long createdAt = MongoConnection.players.find(and(eq("tournament_id", tourID), eq("username", username))).first().getLong("createdAt");
        return MongoConnection.matches.find(
            and(
                eq("tournament_id", tourID),
                Filters.elemMatch("result", and(eq("username", username), eq("score", score))),
                gt("createdAt", createdAt))).into(new ArrayList<Document>());
    }

    public static List<Document> getUploadedPlayers() {
        return MongoConnection.players.find(eq("srcStatus", "uploaded")).sort(descending("createdAt")).into(new ArrayList<Document>());
    }

    public static Document getTournamentByID(ObjectId tourID) {
        return MongoConnection.tournaments.find(eq("_id", tourID)).first();
    }

    public static List<Document> getReadyPlayers(ObjectId tourID) {
        return MongoConnection.players.find(and(eq("tournament_id", tourID), eq("status", "ready"))).into(new ArrayList<Document>());
    }

    public static boolean isPlayerReady(ObjectId playerID) {
        return MongoConnection.players.find(eq("_id", playerID)).first().getString("status").equals("ready");
    }

    public static Document getTournamentByName(String tourName) {
        return MongoConnection.tournaments.find(eq("name", tourName)).first();
    }

    public static Document getGameByName(String gameName) {
        return MongoConnection.games.find(eq("name", gameName)).first();
    }

    public static Document createTournament(String tourName, Document game) {
        Document tourDoc = new Document("name", tourName)
            .append("game", game.getString("name"))
            .append("gameid", game.get("_id"))
            .append("hash", "")
            .append("status", "stop")
            .append("archived", "no")
            .append("createdAt", System.currentTimeMillis());

        MongoConnection.tournaments.insertOne(tourDoc);
        return tourDoc;
    }

    public static void uploadPlayer(ObjectId tourID, String username, String pathToZip) {
        Document player = MongoConnection.players.find(and(eq("tournament_id", tourID), eq("username", username))).first();
        Document tournament = MongoConnection.tournaments.find(eq("_id", tourID)).first();
        if (player == null) {
            MongoConnection.players.insertOne(
                new Document("username", username)
                    .append("tournament_id", tournament.getObjectId("_id"))
                    .append("tournament", tournament.getString("name"))
                    .append("pathToZip", pathToZip)
                    .append("pathToClasses", "")
                    .append("status", "busy")
                    .append("srcStatus", "uploaded")
                    .append("rating", gameInfo.getDefaultRating().getConservativeRating())
                    .append("mu", gameInfo.getDefaultRating().getMean())
                    .append("sigma", gameInfo.getDefaultRating().getStandardDeviation())
                    .append("numMatch", 0)
                    .append("win", 0)
                    .append("lose", 0)
                    .append("draw", 0)
                    .append("createdAt", System.currentTimeMillis()));
            return;
        }

        MongoConnection.players.findOneAndUpdate(
            and(eq("tournament_id", tourID), eq("username", username)),
            and(set("pathToZip", pathToZip), set("srcStatus", "uploaded")));

    }

    public static Document createDefaultRatingUser(String username) {
        //GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        return new Document("username", username)
            .append("rating", gameInfo.getDefaultRating().getConservativeRating())
            .append("mu", gameInfo.getDefaultRating().getMean())
            .append("sigma", gameInfo.getDefaultRating().getStandardDeviation())
            .append("numMatch", 0)
            .append("win", 0)
            .append("lose", 0)
            .append("draw", 0);
    }

    public static List<Document> getRanksByTourID(ObjectId tourID) {
        return MongoConnection.players.find(eq("tournament_id", tourID)).into(new ArrayList<Document>());
    }

    public static Document getGameByID(ObjectId gameid) {
        return MongoConnection.games.find(eq("_id", gameid)).first();
    }

    public static List<Document> getRunningTournament() {
        return MongoConnection.tournaments.find(eq("status", "running")).into(new ArrayList<Document>());
    }

    public static void deletePreviousMatches(ObjectId tourID, String username) {
        MongoConnection.matches.deleteMany(
            and(
                eq("tournament_id", tourID),
                Filters.elemMatch("result", eq("username", username))
            ));
    }
}
