package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.HashMap;

/*
 * Main class to run AI contest.
 */
public class Contest {

    public static void main(String[] args) throws InterruptedException {
        // Deamon program to unzip and compile incoming players
        Submission submission = new Submission();
        Thread submissionThread = new Thread(submission);
        submissionThread.start();

        // Updates game list to sync with game repo
        PopulateGames pGame = new PopulateGames();
        Thread pGameT = new Thread(pGame);
        pGameT.start();


        // Runs tournaments
        MongoConnection con = new MongoConnection();
        MongoCollection<Document> players = con.players;
        MongoCollection<Document> tournaments = con.tournaments;

        Map<String, TournamentManager> tournamentMap = new HashMap<String, TournamentManager>();
        while (true) {
            // each tournament does match making and updates match
            for (Document aTournament : tournaments.find(eq("status", "running"))) {
                String tourName = aTournament.getString("name");
                String tourid = aTournament.getString("_id");
                try {
                    if (tournamentMap.get(tourName) == null)
                        tournamentMap.put(tourName, new TournamentManager(tourid, tourName, con));
                    tournamentMap.get(tourName).matchMaking();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(3 * 1000);
        }
    }
}
