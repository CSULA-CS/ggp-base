package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import jskills.*;
import jskills.trueskill.TwoPlayerTrueSkillCalculator;
import org.apache.commons.io.FileUtils;
import org.bson.Document;
import org.ggp.base.player.GamePlayer;
import org.ggp.base.player.gamer.Gamer;
import org.ggp.base.server.GameServer;
import org.ggp.base.server.event.ServerMatchUpdatedEvent;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.game.GameRepository;
import org.ggp.base.util.match.Match;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.statemachine.Role;
import org.python.antlr.ast.Str;
import org.xml.sax.SAXException;

import javax.print.Doc;
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

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;
import static java.util.Arrays.asList;

public class TournamentManager implements Observer {
    private TwoPlayerTrueSkillCalculator twoPlayersCalculator;
    private MongoConnection con;
    protected MongoCollection<Document> games;
    protected MongoCollection<Document> matches;
    protected MongoCollection<Document> tournaments;
    protected MongoCollection<Document> players;
    // map matchid with a list of players for that match
    private final Map<String, List<GamePlayer>> playerMap;
    private final Set<String> busyUsers;
    private String tournament;
    private final int numPlayers = 2;
    private ReplayBuilder replayBuilder;

    public TournamentManager(String touneyName, MongoConnection connection, ReplayBuilder theReplay) {
        twoPlayersCalculator = new TwoPlayerTrueSkillCalculator();
        con = connection;
        matches = con.matches;
        tournaments = con.tournaments;
        players = con.players;
        games = con.games;
        tournament = touneyName;
        busyUsers = Collections.synchronizedSet(new HashSet<String>());
        playerMap = Collections.synchronizedMap(new HashMap<String, List<GamePlayer>>());
        replayBuilder = theReplay;
    }

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

            synchronized (busyUsers) {
                busyUsers.removeAll(match.getPlayerNamesFromHost());
            }

            // update DB
            // List<String> usernames = match.getPlayerNamesFromHost();
            // if a tournament hasn't started, set player's skills to default
            try {
                //updateOneVsOneMatch(match);
                updateRankings(match);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        return;
    }

    /*
     * Matches a least played user with an opponent making best match quality.
     */
    public void matchMaking() throws Exception {
        System.out.println("---------------------------");

        List<Document> ranks = getCurrentRankings();
        matchLeastPlayedUserByBestMatchQuality(ranks);
    }

    private Collection<ITeam> setupTeams(Document userOne, Document userTwo) throws Exception {
        Player<String> player1 = new Player<>(userOne.getString("username"));
        Player<String> player2 = new Player<>(userTwo.getString("username"));
        Rating p1Rating = new Rating(userOne.getDouble("mu"), userOne.getDouble("sigma"));
        Rating p2Rating = new Rating(userTwo.getDouble("mu"), userTwo.getDouble("sigma"));
        Team team1 = new Team(player1, p1Rating);
        Team team2 = new Team(player2, p2Rating);
        return Team.concat(team1, team2);
    }

    /*
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


    /* **** Currently Not used *******
     * Matches the least played users with similar skilled player. Match quality > 0.5
     */
    private boolean matchLeastPlayedUserWithSimilarSkillOpponent(List<Document> ranks) throws Exception {
        // DEBUG
        System.out.print("matchLeastPlayedUserWithSimilarSkillOpponent::");

        sortByNumberOfMatch(ranks);
        List<String> pickedUsers = new ArrayList<>();
        Document leastPlayedUserDoc = ranks.get(0);
        pickedUsers.add(leastPlayedUserDoc.getString("username"));
        sortByRating(ranks);

        for (Document rank : ranks) {
            if (!busyUsers.contains(rank.getString("username")) && rank != leastPlayedUserDoc) {
                Collection<ITeam> teams = setupTeams(leastPlayedUserDoc, rank);
                if (twoPlayersCalculator.calculateMatchQuality(GameInfo.getDefaultGameInfo(), teams) > 0.38) {
                    // adds an opponent.
                    pickedUsers.add(rank.getString("username"));
                    for (String username : pickedUsers)
                        System.out.println("username: " + username);

                    playOneVsOne(pickedUsers);
                    return true;
                }
            }
        }

        System.out.println("No match!");
        return false;
    }

    /*
     * Matches the least played users with another player making the best match quality.
     */
    private boolean matchLeastPlayedUserByBestMatchQuality(List<Document> ranks) throws Exception {
        // DEBUG
        System.out.print("matchLeastPlayedUserByBestMatchQuality::");

        sortByNumberOfMatch(ranks);
        List<String> pickedUsers = new ArrayList<>();
        // Finds which couple makes a best match quality.
        double bestQuality = -100.00;
        for (Document p1 : ranks) {
            for (int i = 0; i < ranks.size(); i++) {
                Document p2 = ranks.get(i);
                String user1 = p1.getString("username");
                String user2 = p2.getString("username");
                if (!busyUsers.contains(user1) && !busyUsers.contains(user2) && user1 != user2) {
                    Collection<ITeam> teams = setupTeams(p1, p2);
                    double quality = twoPlayersCalculator.calculateMatchQuality(GameInfo.getDefaultGameInfo(), teams);
                    if (quality > bestQuality) {

                        bestQuality = quality;
                        if (pickedUsers.size() > 0)
                            pickedUsers.clear();

                        pickedUsers.add(p1.getString("username"));
                        pickedUsers.add(p2.getString("username"));
                        for (String username : pickedUsers)
                            System.out.println("username: " + username);

                        playOneVsOne(pickedUsers);
                        return true;
                    }
                }
            }
        }


        System.out.println("No match!");
        return false;
    }

    /*
     * Finds new user not in current ranking, adds to a set of current users.
     * Then builds up new rankings.
     */
    private List<Document> getCurrentRankings() throws Exception {
        if (latestMatch(tournament) == null)
            return defaultRatingUsers(usersInTournament());

        List<Document> currentRankings = (List<Document>) latestMatch(tournament).get("ranks");
        Set<String> userSet = new HashSet<>();
        for (Document rank : currentRankings) {
            userSet.add(rank.getString("username"));
        }

        List<String> usersNotInRanking = new ArrayList<>();
        for (String username : usersInTournament()) {
            if (!userSet.contains(username)) {
                usersNotInRanking.add(username);
            }
        }

        List<Document> usersNotInRankingDoc = new ArrayList<>();
        if (usersNotInRanking.size() > 0) {
            usersNotInRankingDoc.addAll(defaultRatingUsers(usersNotInRanking));
            currentRankings.addAll(usersNotInRankingDoc);
        }
        createRankings(currentRankings);

        System.out.println("number user = " + currentRankings.size());
        return currentRankings;
    }

    private void sortByNumberOfMatch(List<Document> ranks) {
        Collections.sort(ranks, new Comparator<Document>() {
            public int compare(Document d1, Document d2) {
                return d1.getInteger("numMatch").compareTo(d2.getInteger("numMatch"));
            }
        });
    }

    private void sortByRating(List<Document> ranks) {
        Collections.sort(ranks, new Comparator<Document>() {
            public int compare(Document d1, Document d2) {
                return d1.getDouble("rating").compareTo(d2.getDouble("rating"));
            }
        });
    }

    /*
     * Returns a list of users having their players status 'compiled'
     */
    private List<String> usersInTournament() {
        // "_id" required by API
        List<Document> playersInTournament =
                players.aggregate(
                        asList(
                                new Document("$match", new Document("tournament", tournament)),
                                new Document("$group", new Document("_id", "$username"))
                        )).into(new ArrayList<Document>());

        List users = new ArrayList<String>();
        for (Document aPlayer : playersInTournament) {
            String username = aPlayer.get("_id").toString();
            // System.out.println("username = " + username);
            Document thisPlayer =
                    players.find(and(
                            eq("username", username),
                            eq("tournament", tournament),
                            eq("status", "compiled"))).first();

            if (thisPlayer != null)
                users.add(username);
        }

        return users;
    }

    /*
     * Updates rankings in database by match result.
     * Called after each match is finished.
     */
    private void updateRankings(Match match) throws Exception {
        /*// init userRankMap from latest ranks
        Map<String, Document> userRankMap = new HashMap<>();

        // No match means no rankings yet, then create one with default values.
        Document latestMatch = latestMatch(tournament);
        if (latestMatch == null) {
            List<Document> userDocs = defaultRatingUsers(usersInTournament());
            for (Document userDoc: userDocs)
                userRankMap.put(userDoc.getString("username"), userDoc);
        }
        // If there is a ranking, pull them up.
        else {
            List<Document> latestRanks = (List<Document>)latestMatch.get("ranks");
            for (Document rank : latestRanks)
                userRankMap.put(rank.getString("username"), rank);
        }

        // List of users from latest match not in current ranking.
        List<String> usersNotInRankings = new ArrayList<>();
        for (String username : match.getPlayerNamesFromHost()) {
            if (!userRankMap.containsKey(username))
                usersNotInRankings.add(username);
        }

        // Initial values for users from latest match not in current ranking.
        List<Document> usersNotInRankingsDoc = new ArrayList<>();
        if (!usersNotInRankings.isEmpty())
            usersNotInRankingsDoc.addAll(defaultRatingUsers(usersNotInRankings));
        if (!usersNotInRankingsDoc.isEmpty()) {
            for (Document eachUser: usersNotInRankingsDoc)
                userRankMap.put(eachUser.getString("username"), eachUser);
        }*/

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

        // updates ratings of current rankings
        for (Map.Entry<IPlayer, Rating> entry : newRatings.entrySet()) {
            String username = entry.getKey().toString();
            Rating newRating = entry.getValue();

            if (userRankMap.containsKey(username)) {
                int numMatch = userRankMap.get(username).getInteger("numMatch") + 1;
                userRankMap.put(username, new Document("username", username)
                        .append("rating", newRating.getConservativeRating())
                        .append("mu", newRating.getMean())
                        .append("sigma", newRating.getStandardDeviation())
                        .append("numMatch", numMatch));
            }
        }

        // rankings
        List<Document> ranks = new ArrayList<Document>(userRankMap.values());
        createRankings(ranks);
        // match data
        List<Document> matchResult = matchResult(match);
        // insert data
        insertNewMatch(tournament, match, matchResult, ranks);
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

    private Document latestMatch(String tournament) {
        if (matches.count(eq("tournament", tournament)) == 0)
            return null;
        return matches.find(eq("tournament", tournament)).sort(descending("createdAt")).first();
    }

    /*
     * Returns username and its score of a match.
     */
    private List<Document> matchResult(Match match) {
        // match result
        List<Document> matchResult = new ArrayList<>();
        for (int i = 0; i < match.getGoalValues().size(); i++)
            matchResult.add(new Document("username", match.getPlayerNamesFromHost().get(i)).append("score", match.getGoalValues().get(i)));

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
                    .append("numMatch", 0);

            defaultUsers.add(userDoc);
        }
        return defaultUsers;
    }


    /*
     * Adds field "ranks" to a list of user document
     */
    private void createRankings(List<Document> ranks) {
        sortByRating(ranks);
        int len = ranks.size();
        for (Document rank : ranks) {
            rank.append("rank", len);
            len--;
        }
    }

    private void insertNewMatch(String tournamentName,
                                Match match, List<Document> matchResult,
                                List<Document> ranks) throws TransformerException, ParserConfigurationException, IOException, XPathExpressionException, SAXException {
        String tour_id = tournaments.find(eq("name", tournamentName)).first().getString("_id");
        Document thisMatch = new Document("tournament", tournament)
                .append("tournament_id", tour_id)
                .append("match_id", match.getMatchId())
                .append("result", matchResult)
                .append("ranks", ranks)
                .append("replay", replayBuilder.getReplayList(match.toXML(), match.getGame().getStylesheet()))
                .append("createdAt", new Date());
        matches.insertOne(thisMatch);
    }

    /*
     *  Saves a match, **Currently not in used**
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

    private void playGame(List<String> hostNames,
                          List<String> playerNames,
                          List<Integer> portNumbers,
                          List<GamePlayer> gamePlayers,
                          String gameKey) throws IOException, InterruptedException {
        //DEBUG
        System.out.println("play game by these users");
        for (String player : playerNames)
            System.out.println(">> " + player);

        Game game = GameRepository.getDefaultRepository().getGame(gameKey);
        int expectedRoles = Role.computeRoles(game.getRules()).size();
        if (hostNames.size() != expectedRoles) {
            throw new RuntimeException("Invalid number of players for game "
                    + gameKey + ": " + hostNames.size() + " vs " + expectedRoles);
        }

        String matchId = tournament + "." + gameKey + "." + System.currentTimeMillis();
        int startClock = 3;
        int playClock = 14;
        Match match = new Match(matchId, -1, startClock, playClock, game);
        match.setPlayerNamesFromHost(playerNames);

        // run players
        for (GamePlayer aGamePlayer : gamePlayers) {
            aGamePlayer.start();
        }

        // update players and match status
        synchronized (playerMap) {
            playerMap.put(matchId, gamePlayers);
        }

        synchronized (busyUsers) {
            busyUsers.addAll(playerNames);
        }

        // run the match
        GameServer server = new GameServer(match, hostNames, portNumbers);
        server.addObserver(this);
        server.start();
        // server.join();  ** Don't use "join", it makes other threads wait for this one.
    }

    private void playOneVsOne(List<String> pickedUsers) throws Exception {
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

        String gameName = tournaments.find(eq("name", tournament)).first().getString("game");
        String gameKey = games.find(eq("name", gameName)).first().getString("key");
        if (gamePlayers.size() == numPlayers) {
            playGame(hostNames, playerNames, portNumbers, gamePlayers, gameKey);
            return;
        }

        // playGame needs ranks(1,2,3) as a return object.
    }

}