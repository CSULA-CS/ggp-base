package org.ggp.base.tournament;

import com.mongodb.client.result.UpdateResult;
import jskills.GameInfo;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.core.ZipFile;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import static com.mongodb.client.model.Sorts.*;
import static com.mongodb.client.model.Filters.*;
import org.bson.Document;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;

import org.apache.commons.io.FileUtils;
import org.bson.types.ObjectId;

import java.io.File;
import java.util.Collection;
import java.io.IOException;


/*
*   A thread for unzipping and compiling uploaded players.
*   A user has to store java files/packages in one package, then compress that package into one zip file.
*   Note: Some printlns to be removed
*/
class Submission implements Runnable {
    private static final String home = System.getProperty("user.home");
    private static final String uploadDir = home + "/.ggp-server/uploads/";
    private static final String compileDir = home + "/.ggp-server/compiled/";
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> players;
    private MongoCollection<Document> tournaments;
    private MongoCollection<Document> leaderboards;
    private MongoCollection<Document> matches;

    public Submission() {
        mongoClient = new MongoClient("localhost", 3001);
        database = mongoClient.getDatabase("meteor");
        players = database.getCollection("players");
        tournaments = database.getCollection("tournaments");
        leaderboards = database.getCollection("tournaments");
        matches = database.getCollection("matches");
    }

    public void unzip(String fileName) throws ZipException, IOException {
        if (!fileName.split("\\.(?=[^\\.]+$)")[1].equals("zip"))
            return;

        String base = fileName.split("\\.(?=[^\\.]+$)")[0];
        String destination = compileDir + base;
        String password = "password";
        String source = uploadDir + fileName;
        ZipFile zipFile = new ZipFile(source);

        if (zipFile.isEncrypted())
            zipFile.setPassword(password);
        
        File uncompressed = new File(destination);
        uncompressed.mkdirs();
        zipFile.extractAll(destination);

        // delete __MACOSX folder
        File[] thingsInDir = uncompressed.listFiles();
        for (File f : thingsInDir) {
            if (f.isDirectory() && f.getName().equals("__MACOSX")) {
                FileUtils.deleteDirectory(f);
            }
        }
    }

    public boolean compile(String playerPackage) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        String[] extensions = {"java"};
        Collection<File> allJavaFiles = FileUtils.listFiles(new File(playerPackage), extensions, true);
        Iterable<? extends JavaFileObject> compilationUnits2 =
           fileManager.getJavaFileObjects(allJavaFiles.toArray( new File[allJavaFiles.size()]) ); // use alternative method

        // reuse the same file manager to allow caching of jar files
        JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, null, null, compilationUnits2);
        boolean result = task.call();
        fileManager.close();
        return result;
    }

    public String compilePlayer(String pathToZip) throws Exception {
        // compile unzipped files, Need only one root package
        String base = pathToZip.split("\\.(?=[^\\.]+$)")[0];
        String playerDir = compileDir + base;
        int numdir = 0;
        String packageName = "";
        File[] thingsInDir = new File(playerDir).listFiles();
        
        // Prevent multiple packages in a zip file.
        for (File f : thingsInDir) {
            if (f.isDirectory()) {
                numdir++;
                packageName = f.getName();
            }
        }
        
        if (numdir == 0 || numdir > 1) {
            System.out.println("Sorry, we need only one package.");
            return null;
        } else {
            System.out.println("playerDir = " + playerDir + "/" + packageName);
            if (compile(playerDir + "/" + packageName))
                return playerDir;
            return null;
        }
    }

    private void resetRating(String tourid, String username) {
        Document latestMatch = matches.find(eq("tournament_id", tourid)).sort(descending("_id")).first();
        ObjectId matchid = latestMatch.getObjectId("_id");

        Document elemMatchCondition = new Document("$elemMatch", new Document("username", username));
        Document ranksCondition = new Document("ranks", elemMatchCondition);

        GameInfo gameInfo = GameInfo.getDefaultGameInfo();
        Document setCondition = new Document("$set",
                new Document("ranks.$.rating", gameInfo.getDefaultRating().getConservativeRating())
                        .append("ranks.$.mu", gameInfo.getDefaultRating().getMean())
                        .append("ranks.$.sigma", gameInfo.getDefaultRating().getStandardDeviation())
        );
        matches.updateOne(and(eq("_id", matchid), ranksCondition), setCondition);
    }

    @Override
    public void run() {

        while (true) {
            for (Document thePlayer : players.find(eq("status", "uploaded")).sort(descending("createdAt"))) {
                String pathToZip = thePlayer.get("pathToZip").toString();
                try {
                    unzip(pathToZip);
                    String pathToClasses = compilePlayer(pathToZip);
                    if (pathToClasses == null) // compiles fail
                        players.updateOne(eq("pathToZip", pathToZip), new Document("$set", new Document("status", "fail")));
                    else {
                        // compiles successfully and resets user's rating.
                        UpdateResult result =
                                players.updateOne(eq("pathToZip", pathToZip),
                                new Document("$set", new Document("pathToClasses", pathToClasses).append("status", "compiled")));
                        Document latestMatch = matches.find().sort(descending("_id")).first();
                        resetRating(latestMatch.getString("tournament_id"), thePlayer.getString("username"));
                    }
                }
                catch(Exception e) {
                    e.printStackTrace();
                    continue;
                }
            }

            // Sleeps for 3 seconds
            try {
                Thread.currentThread().sleep(3 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //System.out.println("Submission --- 5 seconds passed ---");
        }
    }
}
