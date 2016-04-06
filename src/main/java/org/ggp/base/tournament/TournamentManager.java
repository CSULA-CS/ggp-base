package org.ggp.base.tournament;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import jskills.*;
import jskills.trueskill.TwoPlayerTrueSkillCalculator;
import org.apache.commons.io.FileUtils;
import org.bson.Document;

import org.bson.types.ObjectId;
import org.ggp.base.player.GamePlayer;
import org.ggp.base.player.gamer.Gamer;
import org.ggp.base.server.GameServer;
import org.ggp.base.server.event.ServerMatchUpdatedEvent;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.game.GameRepository;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.match.Match;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.ui.GameStateRenderer;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Sorts.descending;
import static java.util.Arrays.asList;

/*
 * Currently supports only two-player game.
 */
public class TournamentManager implements Observer {
    private final int numPlayers;
    // map matchid with a list of players for that match
    private final Map<String, List<GamePlayer>> playerMap;
    // schedulingQueue is a queue of finished matches but not inserted to DB yet.
    private final List<Match> schedulingQueue;
    private final Set<String> busyUsers;

    // game info
    private final String gameid;
    private final String gameKey;
    private final Game game;
    private final List<Role> roles;
    protected MongoCollection<Document> games;
    protected MongoCollection<Document> matches;
    protected MongoCollection<Document> tournaments;
    protected MongoCollection<Document> players;
    private TwoPlayerTrueSkillCalculator twoPlayersCalculator;
    private MongoConnection con;
    private String tourid;
    private String tournament;

    // Fixes clock for now, this can be set from UI later.
    private int startClock = 3;
    private int playClock = 14;

    public TournamentManager(String touneyName, MongoConnection connection) {
        twoPlayersCalculator = new TwoPlayerTrueSkillCalculator();
        con = connection;
        matches = con.matches;
        tournaments = con.tournaments;
        players = con.players;
        games = con.games;

        tournament = touneyName;
        // Make these collections synchronized in case if later we use threads to manage this class.
        busyUsers = Collections.synchronizedSet(new HashSet<String>());
        playerMap = Collections.synchronizedMap(new HashMap<String, List<GamePlayer>>());
        schedulingQueue = Collections.synchronizedList(new ArrayList<Match>());

        // Computes number of roles/players for this game.
        tourid = tournaments.find(eq("name", tournament)).first().getString("_id");
        gameid = tournaments.find(eq("_id", tourid)).first().getString("gameid");
        gameKey = games.find(eq("_id", new ObjectId(gameid))).first().getString("key");
        game = GameRepository.getDefaultRepository().getGame(gameKey);
        roles = Role.computeRoles(game.getRules());
        numPlayers = roles.size();

        startSchedulingThread();
    }

    public TournamentManager(String theTourid, String touneyName, MongoConnection connection) {
        twoPlayersCalculator = new TwoPlayerTrueSkillCalculator();
        con = connection;
        matches = con.matches;
        tournaments = con.tournaments;
        players = con.players;
        games = con.games;
        tourid = theTourid;
        tournament = touneyName;
        // Make these collections synchronized in case if later we use threads to manage this class.
        busyUsers = Collections.synchronizedSet(new HashSet<String>());
        playerMap = Collections.synchronizedMap(new HashMap<String, List<GamePlayer>>());
        schedulingQueue = Collections.synchronizedList(new ArrayList<Match>());

        // Computes number of roles/players for this game.
        gameid = tournaments.find(eq("_id", tourid)).first().getString("gameid");
        gameKey = games.find(eq("_id", new ObjectId(gameid))).first().getString("key");
        game = GameRepository.getDefaultRepository().getGame(gameKey);
        roles = Role.computeRoles(game.getRules());
        numPlayers = roles.size();

        startSchedulingThread();
    }

    public UpdateResult addUser(String username) throws Exception {
        return tournaments.updateOne(eq("_id", tourid),
                new Document("$push", new Document("users", new Document("username", username)))
        );
    }

    /*
     * For testing: adds a compiled player
     */
    public void addPlayer(String tourName, String username, String pathToClasses) {
        Document aPlayer = new Document("username", username)
            .append("tournament", tourName)
            .append("pathToZip", "")
            .append("pathToClasses", pathToClasses)
            .append("status", "compiled")
            .append("createdAt", new Date());
        players.insertOne(aPlayer);
    }

    /*
     * For testing: delete a user from this tournament
     */
    public UpdateResult deleteUser(String username) {
        return tournaments.updateOne(eq("name", tournament),
                new Document("$pull",
                    new Document("users", new Document("username", username))));
    }

    /*
     * For testing: delete a last uploaded player by username
     */
    public Document deleteLatestPlayer(String username) {
        Date latestMatchDate =
            players.find(and(
                eq("tournament", tournament),
                eq("username", username),
                eq("status", "compiled"))).sort(descending("createdAt")).first().getDate("createdAt");
        System.out.println("Delete a match on this date : " + latestMatchDate);
        return players.findOneAndDelete(
            and(
                eq("tournament", tournament),
                eq("username", username),
                eq("createdAt", latestMatchDate)
            ));
    }

    /*
     * For testing: check if this tournament has this user
     */
    public boolean hasThisUser(String userToAdd) {
        Document elemMatch = new Document("users.username", userToAdd);
        if (tournaments.find(and(eq("name", tournament), new Document("elemMatch", elemMatch))).first() != null)
            return true;
        return false;
    }

    /*
     * For testing: check if this user has any compiled players
     **  Maybe need this method later **
     */
    public boolean doesUserHaveCompiledPlayer(String username) {
        Document aPlayer =
            players.find(
                and(
                    eq("tournament", tournament),
                    eq("username", username))
            ).sort(descending("createdAt")).first();
        if (aPlayer != null)
            return true;
        return false;
    }

    /*
     * For testing: returns a list of compiled player by username
     */
    public List<Document> compiledPlayersByUser(String username) {
        return
            players.find(
                and(
                    eq("tournament", tournament),
                    eq("username", username),
                    eq("status", "compiled")
                )
            ).into(new ArrayList<Document>());
    }

    /*
     * Returns a document of latest match
     */
    public Document latestMatch() {
        if (matches.count(eq("tournament_id", tourid)) == 0)
            return null;
        return matches.find(eq("tournament_id", tourid)).sort(descending("createdAt")).first();
    }

    /*
     * Returns a list of documents of username
     * Used in TournamentManagerTest
     */
    public List<Document> usersInTournament() {
        return (List) tournaments.find(and(eq("name", tournament))).first().get("users");
    }

    /*
     * Returns a list of users having their players status 'compiled'
     */
    private List<String> usersHavingCompiledPlayer() {
        // "_id" required by API
        List<Document> playersInTournament =
                players.aggregate(
                        asList(
                                new Document("$match", new Document("tournament_id", tourid)),
                                new Document("$group", new Document("_id", "$username"))
                        )).into(new ArrayList<Document>());

        List users = new ArrayList<String>();
        for (Document aPlayer : playersInTournament) {
            String username = aPlayer.get("_id").toString();
            // System.out.println("username = " + username);
            Document thisPlayer =
                    players.find(and(
                            eq("username", username),
                            eq("tournament_id", tourid),
                            eq("status", "compiled"))).first();

            if (thisPlayer != null)
                users.add(username);
        }

        return users;
    }


    /*
     * Finds new user not in current ranking, adds to a set of current users.
     * Then builds up new rankings.
     */
    public List<Document> getCurrentRankings() throws Exception {
        if (latestMatch() == null)
            return defaultRatingUsers(usersHavingCompiledPlayer());

        // latest match
        System.out.println(">> getCurrentRankings");
        List<Document> rankings = (List) latestMatch().get("ranks");
        List<String> currentUsers = new ArrayList();
        for (Document rank: rankings)
            currentUsers.add(rank.getString("username"));

        // users joined after the latest match and their players are successfully compiled.
        List<Document> newUsers =
                players.find(
                        and(
                                eq("tournament_id", tourid),
                                eq("status", "compiled"),
                                nin("username", currentUsers)
                        )
                ).into(new ArrayList());

        if (newUsers.size() > 0) {
            List<String> defaultUsers = new ArrayList();
            for (Document user: newUsers)
                defaultUsers.add(user.getString("username"));
            List<Document> newUsersWithDefaultRatings = defaultRatingUsers(defaultUsers);
            rankings.addAll(newUsersWithDefaultRatings);
        }

        return rankings;
    }

    /*
     * Shuts down all players
     */
    public void shutdown() {
        for (String match : playerMap.keySet())
            for (GamePlayer aPlayer : playerMap.get(match))
                aPlayer.shutdown();

        con.close();
    }

    @Override
    public void observe(Event genericEvent) {
        if (!(genericEvent instanceof ServerMatchUpdatedEvent)) return;
        ServerMatchUpdatedEvent event = (ServerMatchUpdatedEvent) genericEvent;
        Match match = event.getMatch();

        //DEBUG
        //System.out.println("Match ID: " + match.getMatchId() + " is running...");

        if (match.isCompleted()) {
            System.out.println("............. Match is completed.");

            // shut down players and remove matchid
            synchronized (playerMap) {
                for (GamePlayer aPlayer : playerMap.get(match.getMatchId())) {
                    System.out.println("............. shutdown a player : " + aPlayer.getName());
                    aPlayer.shutdown();
                }
                playerMap.remove(match.getMatchId());
            }

            synchronized (schedulingQueue) {
                schedulingQueue.add(match);
            }
            return;
        }

        return;
    }

    /*
     * Matches a least played user with an opponent making best match quality.
     */
    public void matchMaking() throws Exception {
        List<Document> ranks = getCurrentRankings();
        if (ranks.size() > 0)
            matchLeastPlayedUserWithBestOpponent(ranks);
        else {
            System.out.println("No players!!");
        }

    }

    /*
     * Update rankings for each match in a queue of matches
     */
    public void updateSchdulingQueue() throws Exception {
        while (!schedulingQueue.isEmpty()) {
            System.out.println(">> updateSchdulingQueue");
            Match match = schedulingQueue.remove(0);
            updateRankings(match);
            synchronized (busyUsers) {
                busyUsers.removeAll(match.getPlayerNamesFromHost());
            }
        }
    }

    /*
     * TrueSkill setup for two-player game
     */
    private Collection<ITeam> setupTeams(Document userOne, Document userTwo) throws Exception {
        Player<String> player1 = new Player<>(userOne.getString("username"));
        Player<String> player2 = new Player<>(userTwo.getString("username"));
        Rating p1Rating = new Rating(userOne.getDouble("mu"), userOne.getDouble("sigma"));
        Rating p2Rating = new Rating(userTwo.getDouble("mu"), userTwo.getDouble("sigma"));
        Team team1 = new Team(player1, p1Rating);
        Team team2 = new Team(player2, p2Rating);
        return Team.concat(team1, team2);
    }

    /* **** Currently Not used *******
     * Shuffles a list of players and finds one pair.
     */
    private void matchByRandom(List<String> users) throws Exception {
        System.out.print("matchByRandom::");
        long seed = System.currentTimeMillis();
        Collections.shuffle(users, new Random(seed));
        List<String> pickedUsers = new ArrayList<>();
        for (String user : users) {
            synchronized (busyUsers) {
                if (!busyUsers.contains(user) && pickedUsers.size() < 2)
                    pickedUsers.add(user);
            }
        }

        if (pickedUsers.size() == numPlayers) {
            playOneVsOne(pickedUsers);
            return;
        }
        System.out.println("Not enough players");
    }

    /*
     * Matches least played user with an opponent making best match quality.
     * Stabilised rankings means we can't find good match by quality(lower_bound_quality).
     * If rankings are stabilised, picks first user uploading a player after latest match and start new match.
     */
    private void matchLeastPlayedUserWithBestOpponent(List<Document> ranks) throws Exception {
        // DEBUG
        System.out.println(">> matchLeastPlayedUserWithBestOpponent");
        if (ranks.size() < numPlayers) {
            System.out.println("Not enough players, Need at least " + numPlayers);
            return;
        }

        double lower_bound_quality = -100.00;  // normally between 0.00 - 1.00, we use -100.00 to find best opponent.
        sortByNumberOfMatch(ranks);

        for (Document p1: ranks) {
            if (bestMatchByCondition(ranks, p1, lower_bound_quality))
                return;
        }

        System.out.println("rankings stabilised !");
        Document freshPlayer = findFreshPlayer();
        if (freshPlayer != null) {
            lower_bound_quality = -100.00;
            bestMatchByCondition(ranks, freshPlayer, lower_bound_quality);
        }
        System.out.println("no fresh player");
    }

    /*
     * Returns players document created after the latest match
     */
    private Document findFreshPlayer()  {
        if (latestMatch() == null)
            return null;

        return players.find(
            and(
                eq("tournament_id", tourid),
                gt("createdAt", latestMatch().getDate("createdAt"))
            )).first();
    }

    /*
     * Matches p1 player to best opponent in a tournament.
     */
    private boolean bestMatchByCondition(List<Document> rankings, Document p1, double lower_bound_quality) throws Exception {
        double bestQuality = lower_bound_quality;
        List<String> pickedUsers = new ArrayList<>();

        for (int i = 0; i < rankings.size(); i++) {
            Document p2 = rankings.get(i);
            String user1 = p1.getString("username");
            String user2 = p2.getString("username");
            Collection<ITeam> teams = setupTeams(p1, p2);
            double quality = twoPlayersCalculator.calculateMatchQuality(GameInfo.getDefaultGameInfo(), teams);

            if (!busyUsers.contains(user1)
                && !busyUsers.contains(user2)
                && !user1.equals(user2)
                && quality > bestQuality) {

                    bestQuality = quality;
                    pickedUsers.clear();
                    pickedUsers.add(user1);
                    pickedUsers.add(user2);
            }
        }

        if (pickedUsers.isEmpty()) {
            return false;
        } else {
            synchronized (busyUsers) {
                busyUsers.addAll(pickedUsers);
            }
            playOneVsOne(pickedUsers);
            return true;
        }
    }

    /*
     * Sorts rankings by number of match users have played
     */
    private void sortByNumberOfMatch(List<Document> ranks) {
        Collections.sort(ranks, new Comparator<Document>() {
            public int compare(Document d1, Document d2) {
                return d1.getInteger("numMatch").compareTo(d2.getInteger("numMatch"));
            }
        });
    }

    /*
     * Sorts rankings by user ratings
     */
    private void sortByRating(List<Document> ranks) {
        Collections.sort(ranks, new Comparator<Document>() {
            public int compare(Document d1, Document d2) {
                return -d1.getDouble("rating").compareTo(d2.getDouble("rating"));
            }
        });
    }

    /*
     * Updates rankings in database by match result.
     * Called after each match is finished.
     */
    private void updateRankings(Match match)  throws Exception {
        System.out.println("............. updateRankings.");
        // gets current rankings and put in a map
        Map<String, Document> userRankMap = new HashMap<>();
        for (Document rank : getCurrentRankings()) {
            userRankMap.put(rank.getString("username"), rank);
        }

        // calculates ratings of users in this match
        String user1 = match.getPlayerNamesFromHost().get(0);
        String user2 = match.getPlayerNamesFromHost().get(1);
        Rating p1Rating = new Rating(userRankMap.get(user1).getDouble("mu"), userRankMap.get(user1).getDouble("sigma"));
        Rating p2Rating = new Rating(userRankMap.get(user2).getDouble("mu"), userRankMap.get(user2).getDouble("sigma"));
        Map<IPlayer, Rating> newRatings = updateOneVsOneRating(match, p1Rating, p2Rating);

        updateWinLoseDraw(match, newRatings, userRankMap);
        // gets a list of rankings from a map, then add field "rank" to each of them
        List<Document> ranks = new ArrayList<Document>(userRankMap.values());
        createRankings(ranks);

        // match data
        List<Document> matchResult = matchResult(match);
        // insert data
        insertNewMatch(match, matchResult, ranks);

        //saveMatch(match);
    }

    /*
     * Updates numMatch, win, lose, draw for each user playing this match
     */
    private void updateWinLoseDraw(Match match, Map<IPlayer, Rating> newRatings, Map<String, Document> userRankMap) {

        List<Rating> ratings = new ArrayList<>();
        Iterator newRatingIter = newRatings.values().iterator();
        while (newRatingIter.hasNext()) {
            ratings.add((Rating) newRatingIter.next());
        }
        Rating rating1 = ratings.get(0);
        Rating rating2 = ratings.get(1);

        String user1 = match.getPlayerNamesFromHost().get(0);
        String user2 = match.getPlayerNamesFromHost().get(1);

        double user1ConservativeRating = rating1.getConservativeRating();
        double user1Mean = rating1.getMean();
        double user1StandardDeviation = rating1.getStandardDeviation();
        int user1NumMatch = userRankMap.get(user1).getInteger("numMatch");
        int user1Win = userRankMap.get(user1).getInteger("win");
        int user1Lose = userRankMap.get(user1).getInteger("lose");
        int user1Draw = userRankMap.get(user1).getInteger("draw");

        double user2ConservativeRating = rating2.getConservativeRating();
        double user2Mean = rating2.getMean();
        double user2StandardDeviation = rating2.getStandardDeviation();
        int user2NumMatch = userRankMap.get(user2).getInteger("numMatch");
        int user2Win = userRankMap.get(user2).getInteger("win");
        int user2Lose = userRankMap.get(user2).getInteger("lose");
        int user2Draw = userRankMap.get(user2).getInteger("draw");

        if (match.getGoalValues().get(0) > match.getGoalValues().get(1)) {
            // first user won
            user1Win++;
            user2Lose++;
        } else if (match.getGoalValues().get(0) < match.getGoalValues().get(1)) {
            // first user lose
            user1Win++;
            user2Lose++;
        } else {
            // draw
            user1Draw++;
            user2Draw++;
        }

        userRankMap.put(user1, new Document("username", user1)
                .append("rating", user1ConservativeRating)
                .append("mu", user1Mean)
                .append("sigma", user1StandardDeviation)
                .append("numMatch", ++user1NumMatch)
                .append("win", user1Win)
                .append("lose", user1Lose)
                .append("draw", user1Draw)
        );

        userRankMap.put(user2, new Document("username", user2)
                .append("rating", user2ConservativeRating)
                .append("mu", user2Mean)
                .append("sigma", user2StandardDeviation)
                .append("numMatch", ++user2NumMatch)
                .append("win", user2Win)
                .append("lose", user2Lose)
                .append("draw", user2Draw)
        );
    }

    /*
     * Returns a map of players and their ratings after a match finished
     */
    private Map<IPlayer, Rating> updateOneVsOneRating(Match match, Rating p1Rating, Rating p2Rating) {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();

        Player<String> player1 = new Player<String>(match.getPlayerNamesFromHost().get(0));
        Player<String> player2 = new Player<String>(match.getPlayerNamesFromHost().get(1));
        Team team1 = new Team(player1, p1Rating);
        Team team2 = new Team(player2, p2Rating);
        Collection<ITeam> teams = Team.concat(team1, team2);

        Map<IPlayer, Rating> newRatings;
        if (match.getGoalValues().get(0) > match.getGoalValues().get(1))
            newRatings = twoPlayersCalculator.calculateNewRatings(gameInfo, teams, 1, 2);
        else if (match.getGoalValues().get(0) < match.getGoalValues().get(1))
            newRatings = twoPlayersCalculator.calculateNewRatings(gameInfo, teams, 2, 1);
        else
            newRatings = twoPlayersCalculator.calculateNewRatings(gameInfo, teams, 1, 1);
        return newRatings;
    }

    /*
     * Finds a compiled player by username, creates its object, gets ready to run.
     */
    private GamePlayer createGamePlayer(String username) throws Exception {
        int port = 9157;
        Document aPlayer =
                players.find(and(
                        eq("status", "compiled"),
                        eq("username", username))).sort(descending("createdAt")).first();

        String pathToClasses = aPlayer.get("pathToClasses").toString();
        URL url = new File(pathToClasses).toURL();
        URL[] urls = new URL[]{url};
        ClassLoader cl = new URLClassLoader(urls);
        String[] extensions = {"class"};
        String packageName = new File(pathToClasses).listFiles()[0].getName();
        String pathToPackage = pathToClasses + "/" + packageName;
        Collection<File> allClassFiles =
                FileUtils.listFiles(new File(pathToPackage), extensions, false);

        // Loop through all class files to find Gamer class.
        for (Iterator<File> it = allClassFiles.iterator(); it.hasNext();) {
            File f = it.next();
            String playerName = f.getName().split("\\.(?=[^\\.]+$)")[0];
            String playerPackage = packageName + "." + playerName;
            Class aClass = cl.loadClass(playerPackage);

            // found one and update player name, status and path to classes.
            if (Gamer.class.isAssignableFrom(aClass)) {
                // Setup players
                Gamer gamer = (Gamer) aClass.newInstance();
                return new GamePlayer(port, gamer);
            }
        }
        return null;
    }

    /*
     * Returns username and its score of the match.
     */
    private List<Document> matchResult(Match match) {
        // match result
        List<Document> matchResult = new ArrayList<>();
        for (int i = 0; i < match.getGoalValues().size(); i++) {
            matchResult.add(
                    new Document("username", match.getPlayerNamesFromHost().get(i))
                            .append("score", match.getGoalValues().get(i))
                            .append("role", roles.get(i).toString())
            );
        }

        return matchResult;
    }

    /*
     * Builds up a list of documents carrying username with default scores of Trueskill
     */
    private List<Document> defaultRatingUsers(List<String> users) {
        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        List<Document> defaultUsers = new ArrayList();
        for (String username : users) {
            Document userDoc = new Document("username", username)
                    .append("rating", gameInfo.getDefaultRating().getConservativeRating())
                    .append("mu", gameInfo.getDefaultRating().getMean())
                    .append("sigma", gameInfo.getDefaultRating().getStandardDeviation())
                    .append("numMatch", 0)
                    .append("win", 0)
                    .append("lose", 0)
                    .append("draw", 0);

            defaultUsers.add(userDoc);
        }
        return defaultUsers;
    }

    /*
     * Adds field "ranks" to a list of rankings
     */
    private void createRankings(List<Document> ranks) {
        sortByRating(ranks);
        for (int i = 0; i < ranks.size(); i++) {
            ranks.get(i).append("rank", i + 1);
        }
    }

    /*
     * Gets xhtml for game states using in replays
     */
    public List getXHTMLReplay(Match match) throws TransformerException, XPathExpressionException, IOException, SAXException, ParserConfigurationException {
        String XSL = match.getGame().getStylesheet();
        List xhtmlList = new ArrayList<>();
        List<Set<GdlSentence>> stateList = match.getStateHistory();
        for (Set<GdlSentence> state : stateList) {
            xhtmlList.add(GameStateRenderer.getXHTML(Match.renderStateXML(state), XSL));
        }

        return xhtmlList;
    }

    /*
     * Adds new match to DB
     */
    private void insertNewMatch(Match match, List<Document> matchResult, List<Document> ranks) throws Exception {
        // String tour_id = tournaments.find(eq("name", tournamentName)).first().getString("_id");
        Document thisMatch = new Document("tournament", tournament)
                .append("tournament_id", tourid)
                .append("match_id", match.getMatchId())
                .append("result", matchResult)
                .append("ranks", ranks)
                .append("replay", getXHTMLReplay(match))
                .append("createdAt", new Date());
        matches.insertOne(thisMatch);

        // improves loading time of the leader board when a number of users reaches 1,000.
        // Creates collection leaderboards to store only rankings.
        // One document per tournament.

        Document rankings = new Document("ranks", ranks);
        // Adds new rankings to leaderboards collection
        Object leaderBoardObj =
                tournaments.find(new Document("_id", tourid)).first().get("leaderBoardId");

        // if there are some matches already, updates the leader board.
        if (leaderBoardObj instanceof ObjectId) {
            con.leaderboards.updateOne(eq("_id", (ObjectId) leaderBoardObj), new Document("$set", rankings));
        } else {
            con.leaderboards.insertOne(rankings);
            Document leaderBoard = new Document("leaderBoardId", rankings.getObjectId("_id"));
            Document updateLeaderBoardIdQuery = new Document("$set", leaderBoard);
            tournaments.updateOne(new Document("_id", tourid), updateLeaderBoardIdQuery);
        }
    }

    /*
     *  **Currently not in used**
     *  Saves a match
     */
    private void saveMatch(Match match) throws IOException {
        System.out.println("saveMatch");
        // create dir to store game results in JSON
        File f = new File(tournament);
        if (!f.exists()) {
            f.mkdir();
            // f = new File(tournament + "/scores");
            f.createNewFile();
        }

        // // Open up the JSON file for this match, and save the match there.
        f = new File(tournament + "/" + match.getMatchId() + ".json");
        if (f.exists()) f.delete();

        BufferedWriter bw = new BufferedWriter(new FileWriter(f));
        bw.write(match.toJSON());
        bw.flush();
        bw.close();

        // Open up the XML file for this match, and save the match there.
        f = new File(tournament + "/" + match.getMatchId() + ".xml");
        if (f.exists()) f.delete();
        bw = new BufferedWriter(new FileWriter(f));
        bw.write(match.toXML());
        bw.flush();
        bw.close();
    }

    /*
     * GGP setup for two-player game
     */
    private void playOneVsOne(List<String> pickedUsers) throws Exception {
        if (pickedUsers.size() != 2) {
            System.out.println("Not enough users to play 1 vs 1 !!!, bye");
            return;
        }

        List<String> hostNames = new ArrayList<String>();
        List<Integer> portNumbers = new ArrayList<>();
        List<String> playerNames = new ArrayList<String>();
        List<GamePlayer> gamePlayers = new ArrayList<GamePlayer>();

        for (String username : pickedUsers) {
            hostNames.add("localhost");
            GamePlayer aPlayer = createGamePlayer(username);
            gamePlayers.add(aPlayer);
            portNumbers.add(aPlayer.getGamerPort());
            playerNames.add(username);
        }

        playGame(hostNames, playerNames, portNumbers, gamePlayers, gameKey);
        // playGame needs ranks(1,2,3) as a return object.
    }

    /*
     * Plays two-player game by GGP
     */
    private void playGame(List<String> hostNames,
                          List<String> playerNames,
                          List<Integer> portNumbers,
                          List<GamePlayer> gamePlayers,
                          String gameKey) throws IOException, InterruptedException {
        //DEBUG
        System.out.println(">> playGame");
        for (String player : playerNames)
            System.out.println("user " + player);

        if (hostNames.size() != numPlayers) {
            throw new RuntimeException("Invalid number of players for game "
                    + gameKey + ": " + hostNames.size() + " vs " + numPlayers);
        }

        String matchId = tournament + "." + gameKey + "." + System.currentTimeMillis();
        Match match = new Match(matchId, -1, startClock, playClock, game);
        match.setPlayerNamesFromHost(playerNames);

        // run players
        for (GamePlayer aGamePlayer : gamePlayers) {
            aGamePlayer.start();
        }

        // run the match
        GameServer server = new GameServer(match, hostNames, portNumbers);
        server.addObserver(this);
        server.start();
        // server.join();  ** Don't use "join", it makes other threads wait for this one.

        // update players and match status
        synchronized (playerMap) {
            playerMap.put(matchId, gamePlayers);
        }
    }

    public void startSchedulingThread() {
        new SchedulingThread().start();
    }

    /*
     * schedulingQueue is a queue of finished matches but not inserted to DB yet.
     * This thread checks every a second if any new match in the queue, then reads info about the match, update rankings
     * and updates DB.
     */
    class SchedulingThread extends Thread {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    ;
                }

                if (!schedulingQueue.isEmpty()) {
                    System.out.println(">> updateSchdulingQueue");
                    Match match = schedulingQueue.remove(0);

                    try {
                        updateRankings(match);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    synchronized (busyUsers) {
                        busyUsers.removeAll(match.getPlayerNamesFromHost());
                    }
                }

            }
        }
    }

}