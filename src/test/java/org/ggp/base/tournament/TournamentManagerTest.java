package org.ggp.base.tournament;

import jskills.trueskill.TwoPlayerTrueSkillCalculator;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Created by Amata on 12/14/2015 AD.
 */
public class TournamentManagerTest extends Assert {

    @Test
    public void newTournamentTest() {
        try {

            // creates new tournament
            String tourName = "NewTicTacToe";
            String gameName = "Tic-Tac-Toe";
            Document newTournament = Contest.createTournament(tourName, gameName);
            ObjectId tourID = newTournament.getObjectId("_id");
            // adds players to new tournament
            String pathToZip = "myRandomPlayer-Sun May 29 2016 00:18:32 GMT-0700 (PDT).zip";
            String[] users = {"user1", "user2", "user3", "user4", "user5"};
            for (String user: users) {
                QueryUtil.uploadPlayer(tourID, user, pathToZip);
            }

            Contest.compilePlayers();

            // Test if number of users in tournament and leader board are equal.

            System.out.println("tourid = " + tourID);
            assertEquals(users.length, QueryUtil.getRanksByTourID(tourID).size());

            TwoPlayerTrueSkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
            TournamentManager2 tm2 = new TournamentManager2(tourID, calculator);
            // Plays 3 matches
            testPlay(tourID, tm2, 2);
            testNumberOfMatch(tourID, tm2);
            testWinLoseDraw(tourID, 100, "win");
            testWinLoseDraw(tourID, 0, "lose");
            testWinLoseDraw(tourID, 50, "draw");

            testNewUploadPlayer(tourID, users[0], pathToZip);
            testCompilePlayers(tourID, tm2);
            testWinLoseDraw(tourID, 100, "win");

            // Plays another 3 matches
            System.out.println("Plays another 3 matches");
            testPlay(tourID, tm2, 2);
            testNumberOfMatch(tourID, tm2);
            testWinLoseDraw(tourID, 100, "win");
            testWinLoseDraw(tourID, 0, "lose");
            testWinLoseDraw(tourID, 50, "draw");

            // deletes this tournament
            MongoConnection.tournaments.deleteMany(eq("name", tourName));
            assertTrue(QueryUtil.getTournamentByName(tourName) == null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void testPlay(ObjectId tourID, TournamentManager2 tm2, int numMatch) throws Exception {
        int i=0;
        long start = System.currentTimeMillis();
        List<Document> candidates = null;
        while (i < numMatch) {
            candidates = tm2.getCandidates();
            if (candidates != null) {
                tm2.playOneVsOne(candidates);
                i++;
            }
            //Thread.sleep(1 * 1000);
        }

        while (QueryUtil.getMatches(tourID, start).size() < numMatch) {
            Thread.sleep(2 * 1000);
        }
    }

    public void testCompilePlayers(ObjectId tourID, TournamentManager2 tm2) {
        while (!tm2.areThesePlayerReady(QueryUtil.getPlayers(tourID))) {
            try {
                Thread.sleep(2 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        try {
            Contest.compilePlayers();
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertEquals("compiled", QueryUtil.getReadyPlayers(tourID).get(0).getString("srcStatus"));
    }

    public void testAfterCompile(ObjectId tourID, String username) {
        assertEquals(0, QueryUtil.getPlayer(tourID, username).getInteger("win").intValue());
        assertEquals(0, QueryUtil.getMatchesWithUserScore(tourID, username, 100).size());
    }

    public void testWinLoseDraw(ObjectId tourID, int score, String result) {
        String username;
        int numResult;
        for (Document player: QueryUtil.getPlayers(tourID)) {
            username = player.getString("username");
            if (player.getInteger(result) != null) {
                System.out.println("username = " + username);
                numResult = player.getInteger(result);
                assertEquals(numResult, QueryUtil.getMatchesWithUserScore(tourID, username, score).size());
            }
        }
    }

    public void testNewUploadPlayer(ObjectId tourID, String username, String pathToZip) {
        QueryUtil.uploadPlayer(tourID, username, pathToZip);
        assertEquals("uploaded", QueryUtil.getUploadedPlayers().get(0).getString("srcStatus"));
        assertEquals(pathToZip, QueryUtil.getUploadedPlayers().get(0).getString("pathToZip"));
    }

    public void testNumberOfMatch(ObjectId tourID, TournamentManager2 tm2) {
        List<Document> players = QueryUtil.getRanksByTourID(tourID);
        tm2.sortByNumberOfMatch(players);
        testRanks(players);
    }

    public void testRanks(List<Document> players) {
        int numMatch = players.get(0).getInteger("numMatch").intValue();
        for (Document player: players) {
            assertTrue(numMatch >= player.getInteger("numMatch").intValue());
            numMatch = player.getInteger("numMatch").intValue();
        }
    }
}
