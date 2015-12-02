package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import external.JSON.JSONObject;
import org.bson.Document;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.game.GameRepository;
import org.ggp.base.util.loader.RemoteResourceLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PopulateGames {

    private static List<Document> loadGame(MongoCollection games) throws Exception {
        GameRepository theRepository = GameRepository.getDefaultRepository();
        List<String> theKeyList = new ArrayList<String>(theRepository.getGameKeys());
        Collections.sort(theKeyList);
        System.out.println(theRepository.toString());

        List<Document> gamesToAdd = new ArrayList<>();
        for (String theKey : theKeyList) {
            Game theGame = theRepository.getGame(theKey);
            String theGameURL = theRepository.getGame(theKey).getRepositoryURL();
            JSONObject theMetadata = RemoteResourceLoader.loadJSON(theGameURL, 20);
            if (theGame.getName() == null)
                continue;

            Document aGame = new Document("name", theGame.getName())
                    .append("key", theKey)
                    .append("description", theGame.getDescription())
                    .append("xsl", theGame.getStylesheet())
                    .append("numRoles", theMetadata.getString("numRoles"));
            gamesToAdd.add(aGame);
            //System.out.println("game = " + theGame.getName());
        }
        System.out.println("Number of games = " + gamesToAdd.size());
        return gamesToAdd;
    }

    public static void main(String[] args) {
        MongoClient mongoClient = new MongoClient("localhost", 3001);
        MongoDatabase database = mongoClient.getDatabase("meteor");
        MongoCollection games = database.getCollection("games");

        try {
            List<Document> allGames = loadGame(games);
            games.insertMany(allGames);
            mongoClient.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
