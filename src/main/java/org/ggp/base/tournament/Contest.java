package org.ggp.base.tournament;

import jskills.SkillCalculator;
import jskills.trueskill.TwoPlayerTrueSkillCalculator;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.*;

/*
 * Main class to run AI contest.
 */
public class Contest {

    public static Document createTournament(String tourName, String gameName) throws  Exception {
        Document game = QueryUtil.getGameByName(gameName);
        return QueryUtil.createTournament(tourName, game);
    }

    public static void compilePlayers() throws Exception {
        Submission submission = new Submission();
        // updates leader board
        for (Document thePlayer: submission.getUploadedPlayers()) {
            submission.compilePlayer(thePlayer);
        }
    }

    private static void setAllPlayerStatusReady() throws Exception {
        for (Document player: QueryUtil.getAllPlayers()) {
            QueryUtil.updatePlayerStatus(player.getObjectId("_id"), "ready");
        }
    }

    public static void main(String[] args) throws InterruptedException {

        // Deamon program to unzip and compile incoming players
        Submission submission = new Submission();
        Thread submissionThread = new Thread(submission);
        submissionThread.start();

        // Updates game list to sync with game repo
        PopulateGames pGame = new PopulateGames();
        Thread pGameT = new Thread(pGame);
        pGameT.start();

        try {
            setAllPlayerStatusReady();
        } catch (Exception e) {
            e.printStackTrace();
        }

        // supports only two player games for now.
        SkillCalculator calculator = new TwoPlayerTrueSkillCalculator();
        Map<ObjectId, TournamentManager2> tournamentMap = new HashMap<ObjectId, TournamentManager2>();
        while (true) {
            // get running tournament
            for (Document tournament: QueryUtil.getRunningTournament()) {
                ObjectId tourID = tournament.getObjectId("_id");
                if (tournamentMap.get(tourID) == null) {
                    tournamentMap.put(tourID, new TournamentManager2(tourID, calculator));
                }
                try {
                    tournamentMap.get(tourID).play();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(3 * 1000);
        }

        // Runs tournaments
//        MongoCollection<Document> tournaments = MongoConnection.tournaments;
//        Map<String, TournamentManager> tournamentMap = new HashMap<String, TournamentManager>();
//        while (true) {
//            // each tournament does match making and updates match
//            for (Document aTournament : tournaments.find(eq("status", "running"))) {
//                String tourName = aTournament.getString("name");
//                String tourid = aTournament.getString("_id");
//                try {
//                    if (tournamentMap.get(tourName) == null)
//                        tournamentMap.put(tourName, new TournamentManager(tourid, tourName));
//                    tournamentMap.get(tourName).matchMaking();
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//            }
//            Thread.sleep(3 * 1000);
//        }
    }
}
