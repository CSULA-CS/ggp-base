package org.ggp.base.tournament;

import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.core.ZipFile;

import static com.mongodb.client.model.Filters.*;

import org.bson.Document;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import javax.tools.StandardJavaFileManager;

import org.apache.commons.io.FileUtils;
import org.bson.types.ObjectId;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.io.IOException;
import java.util.List;


/*
*   A thread for unzipping and compiling uploaded players.
*   A user has to store java files/packages in one package, then compress that package into one zip file.
*   Note: Some printlns to be removed
*/
class Submission implements Runnable {

    private static final String home = System.getProperty("user.home");
    private static final String uploadDir = home + "/.ggp-server/uploads/";
    private static final String compileDir = home + "/.ggp-server/compiled/";

    public Submission() {

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

    public String compilePath(String pathToZip) throws Exception {
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

    public void setPlayerCompileFailed(ObjectId playerID) {
        MongoConnection.players.updateOne(eq("_id", playerID), new Document("$set", new Document("srcStatus", "fail")));
    }

    public void compilePlayer(Document thePlayer) throws Exception {
        String pathToZip = thePlayer.getString("pathToZip");
        ObjectId playerID = thePlayer.getObjectId("_id");
        unzip(pathToZip);
        String pathToClasses = compilePath(pathToZip);

        // compiles failed
        if (pathToClasses == null) {
            setPlayerCompileFailed(playerID);
        }
        else {
            // Adds new rankings to leaderboards collection
            ObjectId tourID = thePlayer.getObjectId("tournament_id");
            String username = thePlayer.getString("username");
            QueryUtil.setPlayerReady(playerID, pathToClasses);
        }
    }

    public List<Document> getUploadedPlayers() {
        return MongoConnection.players.find(eq("srcStatus", "uploaded")).into(new ArrayList<Document>());
    }

    @Override
    public void run() {
        while (true) {
            for (Document thePlayer : getUploadedPlayers()) {
                try {
                    compilePlayer(thePlayer);
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
        }
    }
}
