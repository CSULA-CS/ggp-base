package org.ggp.base.tournament;

import org.ggp.base.player.GamePlayer;
import org.ggp.base.player.gamer.Gamer;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;

import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.core.ZipFile;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.Block;
import static com.mongodb.client.model.Sorts.*;
import static com.mongodb.client.model.Filters.*;
import org.bson.Document;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;

import org.apache.commons.io.FileUtils;
import org.python.antlr.op.Sub;

import java.io.File;
import java.nio.file.Files;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import java.io.IOException;
import java.lang.IllegalStateException;
import java.lang.RuntimeException;


/*
*   Some printlns to be removed
*/
class Submission implements Runnable {
    private static final String home = System.getProperty("user.home");
    private static final String uploadDir = home + "/.ggp-server/uploads/";
    private static final String compileDir = home + "/.ggp-server/compiled/";
    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> players;
    private MongoCollection<Document> tournaments;

    public Submission() {
        mongoClient = new MongoClient("localhost", 3001);
        database = mongoClient.getDatabase("meteor");
        players = database.getCollection("players");
        tournaments = database.getCollection("tournaments");
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

    @Override
    public void run() {

        while (true) {
            for (Document thePlayer : players.find(eq("status", "uploaded")).sort(descending("createdAt"))) {
                String pathToZip = thePlayer.get("pathToZip").toString();
                try {
                    unzip(pathToZip);
                    String pathToClasses = compilePlayer(pathToZip);
                    if (pathToClasses == null)
                        players.updateOne(eq("pathToZip", pathToZip), new Document("$set", new Document("status", "fail")));
                    else
                        players.updateOne(eq("pathToZip", pathToZip),
                                new Document("$set", new Document("pathToClasses", pathToClasses).append("status", "compiled")));
                }
                catch(Exception e) {
                    e.printStackTrace();
                    continue;
                }
            }

            try {
                Thread.currentThread().sleep(5 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            //System.out.println("Submission --- 5 seconds passed ---");
        }
    }
}
