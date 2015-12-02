package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;


public class MongoConnection {
    private MongoDatabase database;
    private MongoClient mongoClient;
    protected MongoCollection<Document> games;
    protected MongoCollection<Document> matches;
    protected MongoCollection<Document> tournaments;
    protected MongoCollection<Document> players;


    public MongoConnection() {
        mongoClient = new MongoClient("localhost", 3001);
        MongoDatabase database = mongoClient.getDatabase("meteor");
        matches = database.getCollection("matches");
        tournaments = database.getCollection("tournaments");
        players = database.getCollection("players");
        games = database.getCollection("games");
    }

    public void close() {
        mongoClient.close();
    }
}
