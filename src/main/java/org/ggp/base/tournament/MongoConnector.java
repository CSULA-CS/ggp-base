package org.ggp.base.tournament;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;

import org.bson.Document;

import javax.print.Doc;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.descending;

class MongoConnector {

    public static void main(String[] args) {
        MongoClient mongoClient = new MongoClient( "localhost" , 3001 );
        MongoDatabase database = mongoClient.getDatabase("meteor");
        MongoCollection<Document> games = database.getCollection("games");
        MongoCollection<Document> players = database.getCollection("players");
        MongoCollection<Document> matches = database.getCollection("matches");

//        Document latestMatch =  matches.find(eq("result.username", "test1")).sort(descending("createdAt")).first();
//        System.out.println("tournament = " + latestMatch.getString("tournament"));
//        System.out.println("tournament = " + latestMatch.getDate("createdAt"));

        Document match = matches.find(eq("tournament", "Bobber Man Tour")).sort(descending("createdAt")).first();
        List<Document> ranks = (List<Document>) match.get("ranks");
        Collections.sort(ranks, new Comparator<Document>() {
            public int compare(Document d1, Document d2) {
                return d1.getInteger("numMatch").compareTo(d2.getInteger("numMatch"));
            }
        });
        for (Document rank : ranks)
            System.out.println("user:" + rank.getString("username") + ", rank" + rank.getInteger("rank:") + ", numMatch:" + rank.getInteger("numMatch"));
        mongoClient.close();
    }
}