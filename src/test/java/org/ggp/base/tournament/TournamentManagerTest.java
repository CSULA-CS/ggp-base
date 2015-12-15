package org.ggp.base.tournament;

import org.junit.Assert;
import org.junit.Test;

/**
 * Created by Amata on 12/14/2015 AD.
 */
public class TournamentManagerTest extends Assert {

    @Test
    public void TournamentManagerTest() {
        try {
            MongoConnection con = new MongoConnection();
            String tourName = "tictactoe";
            String userToAdd = "user0";
            String randomPlayerPath = "/Users/Amata/.ggp-server/compiled/myRandomPlayer-Thu Dec 03 2015 16:33:15 GMT-0800 (PST)";
            TournamentManager tm = new TournamentManager(tourName, con, null);
            tm.deleteLatestPlayer(userToAdd);
            tm.deleteUser(userToAdd);

            int numUser = tm.usersInTournament().size();
            int numUserInRanking = tm.getCurrentRankings().size();
            int numCompiledPlayerByNewUser = tm.compiledPlayersByUser(userToAdd).size();

            // adds user, if this user is not in this tournament yet.
            tm.addUser(userToAdd);
            tm.addPlayer(tourName, userToAdd, randomPlayerPath);
            assertEquals(numUser + 1, tm.usersInTournament().size());
            assertEquals(numUserInRanking + 1, tm.getCurrentRankings().size());
            assertEquals(numCompiledPlayerByNewUser + 1, tm.compiledPlayersByUser(userToAdd).size());

            // deletes user and player just added
//            tm.deleteLatestPlayer(userToAdd);
//            tm.deleteUser(userToAdd);
//            assertEquals(numUser, tm.usersInTournament().size());
//            assertEquals(numUserInRanking, tm.getCurrentRankings().size());
//            assertEquals(numCompiledPlayerByNewUser, tm.compiledPlayersByUser(userToAdd).size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
