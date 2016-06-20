package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;


public class MongoConnection {
    // 3001 for dev, 27017 for production
    private static final int PORT = 3001;
    private static  MongoClient mongoClient = new MongoClient("localhost", PORT);
    private static MongoDatabase database = mongoClient.getDatabase("meteor");
    public static MongoCollection<Document> matches = database.getCollection("matches");
    public static MongoCollection<Document> tournaments = database.getCollection("tournaments");
    public static MongoCollection<Document> players = database.getCollection("players");
    public static MongoCollection<Document> games = database.getCollection("games");
    public static MongoCollection<Document> leaderboards = database.getCollection("leaderboards");

    public void close() {
        mongoClient.close();
    }
}
