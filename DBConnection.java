package SemanticRelatedness;

import de.tudarmstadt.ukp.wikipedia.api.DatabaseConfiguration;
import de.tudarmstadt.ukp.wikipedia.api.WikiConstants;
import de.tudarmstadt.ukp.wikipedia.api.Wikipedia;
import de.tudarmstadt.ukp.wikipedia.api.exception.WikiApiException;
import de.tudarmstadt.ukp.wikipedia.api.exception.WikiInitializationException;
import de.tudarmstadt.ukp.wikipedia.api.hibernate.WikiHibernateUtil;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import org.hibernate.Session;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 *
 * @author Basel A. Alhaj
 */
public class DBConnection {
    private static DatabaseConfiguration dbConfig;
    private static Wikipedia wiki;
    
    private static Connection connection = null;
    private static String connectionUrl = "jdbc:mysql://localhost/wikidump?characterEncoding=utf8";
    private static String connectionUser = "root";
    private static String connectionPassword = "";

    private static String path = System.getProperty("user.dir") + "\\MyGraph\\";
    private static String categoryGraphFileName = "CategoryGraph";
    private static String verticesDepthMapFileName = "verticesLongestDepthMap";
    private static String descendantsMapFileName = "vertexDescendantsCountMap";
    
    private static DirectedGraph<Integer, DefaultEdge> loadedGraph;
    private static Map<Integer, List<Integer>> loadedVDepthMap;
    private static Map<Integer, Integer> loadedVDescendantsMap;
    
    private static Integer loadedGraphDepth;
    
    public static DirectedGraph<Integer, DefaultEdge> loadGraph(String location) throws IOException, ClassNotFoundException {
        File file = new File(location);
        if (!file.canWrite()) {
            throw new IOException("Cannot read from file: " + location);
        }
        
        DirectedGraph<Integer, DefaultEdge> categoryGraph;
        BufferedInputStream fin;
        ObjectInputStream ois;
        fin = new BufferedInputStream(new FileInputStream(file));
        ois = new ObjectInputStream(fin);
        categoryGraph = (DirectedGraph<Integer, DefaultEdge>) ois.readObject();
        ois.close();
        return categoryGraph;
    }
        
    public static Map loadMap(String location) throws IOException, ClassNotFoundException {
        File file = new File(location);
        if (!file.canWrite()) {
            throw new IOException("Cannot read from file: " + location);
        }
        
        Map<?,?> map;
        BufferedInputStream fin;
        ObjectInputStream ois;
        fin = new BufferedInputStream(new FileInputStream(file));
        ois = new ObjectInputStream(fin);
        map = (Map<?,?>) ois.readObject();
        ois.close();
        return map;
    }
    
    public static void setConfiguration(boolean graphStatus) throws WikiInitializationException, WikiApiException, IOException, ClassNotFoundException{
        dbConfig = new DatabaseConfiguration();
        
        dbConfig.setHost("localhost");
        dbConfig.setDatabase("wikidump");
        dbConfig.setUser("root");
        dbConfig.setPassword("");
        dbConfig.setLanguage(WikiConstants.Language.arabic);

        wiki = new Wikipedia(dbConfig);
        
        if(graphStatus){
            //Load Graph File
            loadedGraph = loadGraph(path + categoryGraphFileName);
            System.out.println("loadedGraph vertexSet size: " + loadedGraph.vertexSet().size());
            System.out.println("loadedGraph edgesSet size: " + loadedGraph.edgeSet().size());
                    
            //Load verticesDepthMap File
            loadedVDepthMap = loadMap(path + verticesDepthMapFileName);
            System.out.println("loadedVDepthMap size: " + loadedVDepthMap.size());
        
            //Get Graph Depth
            setLoadedGraphDepth();
            
            //Load vertexDescendantsMap File
            loadedVDescendantsMap = loadMap(path + descendantsMapFileName);
            System.out.println("loadedVDescendantsCountMap size: " + loadedVDescendantsMap.size());
        }
    }
    
    public static Connection getConnection () {
        if (connection != null){
            return connection;
        }
        try{
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            connection = DriverManager.getConnection(connectionUrl, connectionUser, connectionPassword);
            return connection; 
        }
        catch(Exception e){
            e.printStackTrace();
        }
        return null;
    }

    public static Session getHibernateSession() {
        return WikiHibernateUtil.getSessionFactory(dbConfig).getCurrentSession();
    }
            
    public static DirectedGraph<Integer, DefaultEdge> getGraph() {
        return loadedGraph;
    }

    private static void setGraph(DirectedGraph<Integer, DefaultEdge> Graph) {
        DBConnection.loadedGraph = Graph;
    }
    
    public static Wikipedia getWiki() {
        return wiki;
    }
    
    private static void setWiki(Wikipedia wiki) {
        DBConnection.wiki = wiki;
    }
    
    public static Map<Integer, List<Integer>> getVerticesDepthMap() {
        return loadedVDepthMap;
    }
    
    private static void setVerticesDepthMap(Map<Integer, List<Integer>> map) {
        DBConnection.loadedVDepthMap = map;
    }

    public static int getLoadedGraphDepth() {
        return loadedGraphDepth;
    }
    
    private static void setLoadedGraphDepth() {
        int longest = 0;
        for (List<Integer> verticesList : getVerticesDepthMap().values()) {
            if (verticesList.size() > longest) {
                longest = verticesList.size();
            }
        }

        longest = longest - 1;

        if (longest < 0) {
            loadedGraphDepth = 0;
        }
        else {
            loadedGraphDepth = longest;
        }
    }
    
    public static Map<Integer, Integer> getLoadedVDescendantsMap() {
        return loadedVDescendantsMap;
    }
    
    private static void setLoadedVDescendantsMap(Map<Integer, Integer> map) {
        DBConnection.loadedVDescendantsMap = map;
    }
}
