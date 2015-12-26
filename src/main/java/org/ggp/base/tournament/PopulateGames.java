package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import external.JSON.JSONException;
import external.JSON.JSONObject;
import org.bson.Document;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.game.GameRepository;
import org.ggp.base.util.loader.RemoteResourceLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;

public class PopulateGames implements Runnable {
    MongoClient mongoClient;
    MongoDatabase database;
    MongoCollection games;

    public PopulateGames() {
        mongoClient = new MongoClient("localhost", 3001);
        database = mongoClient.getDatabase("meteor");
        games = database.getCollection("games");
    }

    /*
     * Adds new games to DB if there exists new ones in a repository.
     */
    private void updateGameList() {
        System.out.println("............. updateGameList.");

        GameRepository theRepository = GameRepository.getDefaultRepository();
        List<String> theKeyList = new ArrayList<String>(theRepository.getGameKeys());
        Collections.sort(theKeyList);

        List<Document> gamesToAdd = new ArrayList<>();
        try {
            if (theKeyList.size() > games.count()) {
                System.out.println("updating a game list.");
                for (String theKey: theKeyList) {
                    Game theGame = theRepository.getGame(theKey);
                    if (theGame.getName() == null) {
                        continue;
                    }

                    if (games.count(eq("name", theGame.getName())) == 0) {
                        String theGameURL = theRepository.getGame(theKey).getRepositoryURL();
                        JSONObject theMetadata = RemoteResourceLoader.loadJSON(theGameURL, 20);
                        Document aGame = new Document("name", theGame.getName())
                                .append("key", theKey)
                                .append("description", theGame.getDescription())
                                .append("xsl", theGame.getStylesheet())
                                .append("numRoles", theMetadata.getString("numRoles"));
                        gamesToAdd.add(aGame);
                    }
                }

                if (gamesToAdd.isEmpty()) {
                    System.out.println("Games with no names will not be added, bye!");
                    return;
                }

                games.insertMany(gamesToAdd);
                System.out.println("done updating.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    /*public static void main(String[] args) {
        MongoClient mongoClient = new MongoClient("localhost", 3001);
        MongoDatabase database = mongoClient.getDatabase("meteor");
        MongoCollection games = database.getCollection("games");
        updateGameList(games);
    }*/

    @Override
    public void run() {
        while (true) {
            updateGameList();
            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
