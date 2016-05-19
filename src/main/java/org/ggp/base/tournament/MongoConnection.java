package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;


public class MongoConnection {
    // 3001 for dev, 27017 for production
    private final int PORT = 3001;
    private MongoDatabase database;
    private MongoClient mongoClient;
    protected MongoCollection<Document> games;
    protected MongoCollection<Document> matches;
    protected MongoCollection<Document> tournaments;
    protected MongoCollection<Document> players;
    protected MongoCollection<Document> leaderboards;


    public MongoConnection() {
        mongoClient = new MongoClient("localhost", PORT);
        database = mongoClient.getDatabase("meteor");
        matches = database.getCollection("matches");
        tournaments = database.getCollection("tournaments");
        players = database.getCollection("players");
        games = database.getCollection("games");
        leaderboards = database.getCollection("leaderboards");
    }

    public MongoCollection<Document> getTournaments() {
        return tournaments;
    }

    public MongoCollection<Document> getPlayers() {
        return players;
    }

    public MongoCollection<Document> getMatches() {
        return matches;
    }

    public void close() {
        mongoClient.close();
    }
}
