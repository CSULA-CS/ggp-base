package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import static com.mongodb.client.model.Filters.eq;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.HashMap;

public class Contest {

    public static void main(String[] args) throws InterruptedException {
        // Deamon program to unzip and compile incoming players
        Submission submission = new Submission();
        Thread submissionThread = new Thread(submission);
        submissionThread.setDaemon(true);
        submissionThread.start();

        // Run tournaments
        MongoConnection con = new MongoConnection();
        ReplayBuilder replay = new ReplayBuilder();
        MongoCollection<Document> players = con.players;
        MongoCollection<Document> tournaments = con.tournaments;

        Map<String, TournamentManager> tournamentMap = new HashMap<String, TournamentManager>();
        while (true) {
            // each tournament does match making
            for (Document aTournament : tournaments.find(eq("status", "running"))) {
                String tourName = aTournament.getString("name");
                try {
                    if (tournamentMap.get(tourName) == null)
                        tournamentMap.put(tourName, new TournamentManager(tourName, con, replay));
                    tournamentMap.get(tourName).matchMaking();
                    tournamentMap.get(tourName).updateSchdulingQueue();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Thread.sleep(3 * 1000);
        }
    }
}
