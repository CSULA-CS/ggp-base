package org.ggp.base.tournament;

import jskills.*;
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
import org.ggp.base.util.observer.*;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.ui.GameStateRenderer;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Sorts.descending;

/**
 * Created by Amata on 5/31/2016 AD.
 */
public class TournamentManager2 implements org.ggp.base.util.observer.Observer {
    private GameInfo gameInfo = GameInfo.getDefaultGameInfo();
    protected Map<String, List<GamePlayer>> matchToPlayersMap;
    protected Document tournament;
    protected Document game;
    private Game theGame;
    protected List<Role> roles;
    private ObjectId tourID;
    private SkillCalculator skillCalculator;
    protected Match latestMatch;
    protected boolean waitForMatch = false;
    // Fixes clock for now, this can be set from UI later.
    private int startClock = 10;
    private int playClock = 10;

    public TournamentManager2(ObjectId tournament_id, SkillCalculator calculator) {
        tournament = QueryUtil.getTournamentByID(tournament_id);
        game = QueryUtil.getGameByID(tournament.getObjectId("gameid"));
        tourID = tournament_id;
        skillCalculator = calculator;
        theGame = GameRepository.getDefaultRepository().getGame(game.getString("key"));
        roles = Role.computeRoles(theGame.getRules());
        matchToPlayersMap = new HashMap<>();
    }

    public List<Document> getCandidates() throws Exception {
        // normally between 0.00 - 1.00, we use 0.203 to make players play more matches.
        double lower_bound_quality = 0.203;

        List<Document> players = QueryUtil.getReadyPlayers(this.tourID);
        sortByNumberOfMatch(players);
        List<Document> candidates = null;
        for (Document p1: players) {
            candidates = bestMatchByCondition(players, p1, lower_bound_quality);
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        return candidates;
    }

    public void play() throws Exception {
        List<Document> candidates = getCandidates();
        if (candidates != null && candidates.size() == roles.size()) playOneVsOne(candidates);
    }

    /*
     * GGP setup for two-player game
     */
    public void playOneVsOne(List<Document> players) throws Exception {
        if (players.size() != roles.size()) {
            System.out.println("Not enough users to play 1 vs 1 !!!, bye");
            return;
        }

        List<String> hostNames = new ArrayList<String>();
        List<Integer> portNumbers = new ArrayList<>();
        List<String> playerNames = new ArrayList<String>();
        List<GamePlayer> gamePlayers = new ArrayList<GamePlayer>();

        for (Document player : players) {
            hostNames.add("localhost");
            GamePlayer aPlayer = createGamePlayer(player);
            gamePlayers.add(aPlayer);
            portNumbers.add(aPlayer.getGamerPort());
            playerNames.add(player.getString("username"));
        }

        String gameKey = game.getString("key");
        playGame(hostNames, players, portNumbers, gamePlayers, gameKey);
        // playGame needs ranks(1,2,3) as a return object.
    }

    /*
     * Plays two-player game by GGP
     */
    private void playGame(List<String> hostNames,
                          List<Document> players,
                          List<Integer> portNumbers,
                          List<GamePlayer> gamePlayers,
                          String gameKey) throws IOException, InterruptedException {

        int numPlayers = Integer.parseInt(game.getString("numRoles"));
        if (hostNames.size() != numPlayers) {
            throw new RuntimeException("Invalid number of players for game " + gameKey + ": " + hostNames.size() + " vs " + numPlayers);
        }

        //DEBUG
        System.out.println(">> playGame");
        List<String> playerNames = new ArrayList<>();
        for (Document player : players) {
            System.out.println("user " + player.getString("username"));
            playerNames.add(player.getString("username"));
        }

        String matchId = tournament.getString("name") + "." + gameKey + "." + System.currentTimeMillis();
        Match match = new Match(matchId, -1, startClock, playClock, theGame);
        match.setPlayerNamesFromHost(playerNames);

        // run players
        for (GamePlayer aGamePlayer : gamePlayers) {
            aGamePlayer.start();
        }

        // run the match
        GameServer server = new GameServer(match, hostNames, portNumbers);
        server.addObserver(this);
        this.matchToPlayersMap.put(match.getMatchId(), gamePlayers);
        for (Document player: players) {
            QueryUtil.updatePlayerStatus(player.getObjectId("_id"), "busy");
        }
        server.start();
        if (this.waitForMatch) server.join();  // Don't use "join", it makes other threads wait for this one.
    }

    /*
 * Finds a compiled player by username, creates its object, gets ready to run.
 */
    private GamePlayer createGamePlayer(Document player) throws Exception {
        int port = 9157;
        Document aPlayer = QueryUtil.getPlayer(player.getObjectId("_id"));
        String pathToClasses = aPlayer.getString("pathToClasses");
        URL url = new File(pathToClasses).toURL();
        URL[] urls = new URL[]{url};
        ClassLoader cl = new URLClassLoader(urls);
        String[] extensions = {"class"};
        String packageName = new File(pathToClasses).listFiles()[0].getName();
        String pathToPackage = pathToClasses + "/" + packageName;
        Collection<File> allClassFiles = FileUtils.listFiles(new File(pathToPackage), extensions, false);

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

    public boolean areThesePlayerReady(List<Document> players) {
        for (Document player: players) {
            if (!QueryUtil.isPlayerReady(player.getObjectId("_id"))) return false;
        }
        return true;
    }

    /*
     * Matches p1 player to best opponent in a tournament.
     */
    public List<Document> bestMatchByCondition(List<Document> rankings, Document p1, double lower_bound_quality) throws Exception {
        List<Document> selectedPlayers = new ArrayList<>();

        for (int i = 0; i < rankings.size(); i++) {
            Document p2 = rankings.get(i);
            String user1 = p1.getString("username");
            String user2 = p2.getString("username");
            Collection<ITeam> teams = setupTeams(p1, p2);
            double quality = skillCalculator.calculateMatchQuality(GameInfo.getDefaultGameInfo(), teams);

            System.out.println("quality = " + quality);
            if (!user1.equals(user2) && quality > lower_bound_quality) {
                selectedPlayers.clear();

                if (Math.random() > 0.5) {
                    selectedPlayers.add(p1);
                    selectedPlayers.add(p2);
                } else {
                    selectedPlayers.add(p2);
                    selectedPlayers.add(p1);
                }

            }
        }

        return selectedPlayers;
    }

    /*
     * Sorts rankings by number of match users have played
     */
    public void sortByNumberOfMatch(List<Document> players) {
        Collections.sort(players, new Comparator<Document>() {
            public int compare(Document d1, Document d2) {
                return d1.getInteger("numMatch").compareTo(d2.getInteger("numMatch"));
            }
        });
    }

    /*
     * Returns a map of players and their ratings after a match finished
     */
    private Map<IPlayer, Rating> updateOneVsOneRating(Match match, List<Document> playerRanks) {
        // calculates ratings of users in this match
        Rating p1Rating = new Rating(playerRanks.get(0).getDouble("mu"), playerRanks.get(0).getDouble("sigma"));
        Rating p2Rating = new Rating(playerRanks.get(1).getDouble("mu"), playerRanks.get(1).getDouble("sigma"));
        Player<String> player1 = new Player<String>(playerRanks.get(0).getString("username"));
        Player<String> player2 = new Player<String>(playerRanks.get(1).getString("username"));
        Team team1 = new Team(player1, p1Rating);
        Team team2 = new Team(player2, p2Rating);
        Collection<ITeam> teams = Team.concat(team1, team2);

        Map<IPlayer, Rating> newRatings;
        if (match.getGoalValues().get(0) > match.getGoalValues().get(1))
            newRatings = this.skillCalculator.calculateNewRatings(gameInfo, teams, 1, 2);
        else if (match.getGoalValues().get(0) < match.getGoalValues().get(1))
            newRatings = this.skillCalculator.calculateNewRatings(gameInfo, teams, 2, 1);
        else
            newRatings = this.skillCalculator.calculateNewRatings(gameInfo, teams, 1, 1);
        return newRatings;
    }

    /*
     * Updates numMatch, win, lose, draw for each user playing this match
     */
    private void updateWinLoseDraw(Match match, Map<IPlayer, Rating> newRatings, List<Document> playerRanks) {
        for (Document player: playerRanks) {
            String username = player.getString("username");
            Rating rating = newRatings.get(new Player<>(username));
            player.put("rating", rating.getConservativeRating());
            player.put("mu", rating.getMean());
            player.put("sigma", rating.getStandardDeviation());
            player.put("numMatch", player.getInteger("numMatch").intValue() + 1);
        }

        Document player1, player2;
        if (match.getPlayerNamesFromHost().get(0).equals(playerRanks.get(0).getString("username"))) {
            player1 = playerRanks.get(0);
            player2 = playerRanks.get(1);
        } else {
            player2 = playerRanks.get(0);
            player1 = playerRanks.get(1);
        }

        int user1Win = player1.getInteger("win").intValue();
        int user2Win = player2.getInteger("win").intValue();
        int user1Lose = player1.getInteger("lose").intValue();
        int user2Lose = player2.getInteger("lose").intValue();
        int user1Draw = player1.getInteger("draw").intValue();
        int user2Draw = player2.getInteger("draw").intValue();
        if (match.getGoalValues().get(0) > match.getGoalValues().get(1)) {
            // first user won
            user1Win++;
            user2Lose++;
        } else if (match.getGoalValues().get(0) < match.getGoalValues().get(1)) {
            // first user lose
            user2Win++;
            user1Lose++;
        } else {
            // draw
            user1Draw++;
            user2Draw++;
        }
        player1.put("win", user1Win);
        player1.put("lose", user1Lose);
        player1.put("draw", user1Draw);
        player2.put("win", user2Win);
        player2.put("lose", user2Lose);
        player2.put("draw", user2Draw);
    }

    public void updateTournament(Match match) throws Exception {
        // updates new match
        List<Document> matchResults = createMatchResult(match);
        insertNewMatch(match, matchResults);

        // collects players in this match
        // List<Document> players = QueryUtil.getPlayers(tourID, match.getPlayerNamesFromHost());
        List<Document> players = new ArrayList<>();
        for (String username: match.getPlayerNamesFromHost()) {
            players.add(QueryUtil.getPlayer(tourID, username));
        }

        // calculates new ratings
        Map<IPlayer, Rating> newRatings = updateOneVsOneRating(match, players);
        updateWinLoseDraw(match, newRatings, players);
        // update rankings in DB
        for (Document aPlayer: players) {
            QueryUtil.updatePlayerSkill(aPlayer);
            QueryUtil.updatePlayerStatus(aPlayer.getObjectId("_id"), "ready");
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

    private void insertNewMatch(Match match, List<Document> matchResult) throws Exception {
        Document thisMatch = new Document("tournament_id", this.tourID)
            .append("result", matchResult)
            .append("replay", getXHTMLReplay(match))
            .append("createdAt", System.currentTimeMillis());

        MongoConnection.matches.insertOne(thisMatch);
    }

    private List<Document> createMatchResult(Match match) {
        // match result
        List<Document> matchResult = new ArrayList<>();
        for (int i = 0; i < match.getGoalValues().size(); i++) {
            matchResult.add(
                new Document("username", match.getPlayerNamesFromHost().get(i))
                    .append("score", match.getGoalValues().get(i))
                    .append("role", this.roles.get(i).toString())
            );
        }

        return matchResult;
    }

    @Override
    public void observe(Event genericEvent) {
        if (!(genericEvent instanceof ServerMatchUpdatedEvent)) return;
        ServerMatchUpdatedEvent event = (ServerMatchUpdatedEvent) genericEvent;
        Match match = event.getMatch();

        if (match.isCompleted()) {
            System.out.println("............. Match is completed.");
            // shuts down players
            for (GamePlayer aPlayer : matchToPlayersMap.get(match.getMatchId())) {
                System.out.println("............. shutdown a player : " + aPlayer.getName());
                aPlayer.shutdown();
            }

            try {
                updateTournament(match);
                matchToPlayersMap.remove(match.getMatchId());
                this.latestMatch = match;
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        return;
    }
}
