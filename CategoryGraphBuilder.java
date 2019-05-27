package SemanticRelatedness;

import de.tudarmstadt.ukp.wikipedia.api.Category;
import de.tudarmstadt.ukp.wikipedia.api.DatabaseConfiguration;
import de.tudarmstadt.ukp.wikipedia.api.WikiConstants;
import de.tudarmstadt.ukp.wikipedia.api.Wikipedia;
import de.tudarmstadt.ukp.wikipedia.api.exception.WikiApiException;
import de.tudarmstadt.ukp.wikipedia.util.ApiUtilities;
import de.tudarmstadt.ukp.wikipedia.util.OS;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 *
 * @author Basel A. Alhaj
 */
public class CategoryGraphBuilder {
    private static DatabaseConfiguration dbConfig;
    private static Wikipedia wiki;
        
    private static String path = System.getProperty("user.dir") + "\\MyGraph\\";
    private static String categoryGraphFileName = "CategoryGraph";
    private static String verticesDepthMapFileName = "verticesLongestDepthMap";
    private static String descendantsMapFileName = "vertexDescendantsCountMap";
    
    private static Connection connection = null;
    private static String connectionUrl = "jdbc:mysql://localhost/wikidump?characterEncoding=utf8";
    private static String connectionUser = "root";
    private static String connectionPassword = "";
    
    private static DirectedGraph<Integer, DefaultEdge> createdGraph;    
    public static DirectedGraph<Integer, DefaultEdge> loadedGraph = null;
    public static Map<Integer, List<Integer>> loadedVDepthMap = null;
    public static Map<Integer, Integer> loadedVDescendantsMap = null;
    
    private static int numberOfVertices;
    private static int numberOfEdges;
    
    private static Map<Integer, Integer> descendantsCountMap = null;
    private static Map<Integer, List<Integer>> verticesDepthMap = null;

    private static int root;
    
    private static double depth = -1;
    
    private enum Color {white, grey, black};
    private static Map<Integer, Color> colorMap;
    
    public static void setNumberOfVertices(int n){
        numberOfVertices = n;
    }
    
    public static int getNumberOfVertices() {
        return numberOfVertices;
    }
    
    public static void setNumberOfEdges(int n){
        numberOfEdges = n;
    }
    
    public static int getNumberOfEdges() {
        return numberOfEdges;
    }
    
    public static Set<Integer> getCategorieIds() {
        Set<Integer> categorySet = new HashSet<Integer>();
        try{  
            Statement stmt = getConnection().createStatement();  
            ResultSet rs = stmt.executeQuery("select cat.pageId from Category as cat");  
            while(rs.next()) {
                categorySet.add(rs.getInt(1));
            }
        }catch(Exception e){ System.out.println(e);}  

        return categorySet;
    }
    
    protected static Set<Integer> getLeafVertices(DirectedGraph<Integer, DefaultEdge> graph) throws WikiApiException {
        Set<Integer> leafVertices = new HashSet<Integer>();
        for (int vertex : graph.vertexSet()) {
            if (graph.outDegreeOf(vertex) == 0) {
                leafVertices.add(vertex);
            }
        }
        return leafVertices;
    }
    
    public static DirectedGraph<Integer, DefaultEdge> constructCategoryGraph(Wikipedia pWiki, Set<Integer> categoriesIds) throws WikiApiException {
        createdGraph = new DefaultDirectedGraph<Integer, DefaultEdge>(DefaultEdge.class);
        wiki = pWiki;

        setNumberOfVertices(createdGraph.vertexSet().size());
        setNumberOfEdges(createdGraph.edgeSet().size());

        for (int cId : categoriesIds) {
            createdGraph.addVertex(cId);
        }

        setNumberOfVertices(createdGraph.vertexSet().size());

        System.out.println(OS.getUsedMemory() + " MB memory used.");
        int progress = 0;
        for (int cId : createdGraph.vertexSet()) {
            progress++;
            ApiUtilities.printProgressInfo(progress, categoriesIds.size(), 10, ApiUtilities.ProgressInfoMode.TEXT, "Adding edges");

            Category cat = wiki.getCategory(cId);

            Set<Integer> inLinks = cat.getParentIDs();
            Set<Integer> outLinks = cat.getChildrenIDs();

            for (int inLink : inLinks) {
                if (createdGraph.vertexSet().contains(inLink)) {
                    if (inLink == cId) {
                        System.out.println("Inlink: Self-loop from vertex " + cId + " (" + cat.getTitle() + ")");
                    }
                    else {
                        createdGraph.addEdge(inLink, cId);
                    }
                }
            }
            
            for (int outLink : outLinks) {
                if (createdGraph.vertexSet().contains(outLink)) {
                    if (outLink == cId) {
                        System.out.println("Outlink: Self-loop for vertex " + cId + " (" + cat.getTitle() + ")");
                    }
                    else {
                        createdGraph.addEdge(cId, outLink);
                    }
                }
            }
        }

        setNumberOfEdges(createdGraph.edgeSet().size());
        
        System.out.println("# of added vertices: " + getNumberOfVertices());
        System.out.println("# of added edges: " + getNumberOfEdges());
        
        removeCycles();
        
        setNumberOfEdges(createdGraph.edgeSet().size());
        
        return createdGraph;
    }
    
    public static void removeCycles() throws WikiApiException {
        DefaultEdge edge = null;
        while ((edge = findCycle()) != null) {    
            System.out.println("Graph contains cycle");
            
            Category sourceCat = wiki.getCategory(createdGraph.getEdgeSource(edge));
            Category targetCat = wiki.getCategory(createdGraph.getEdgeTarget(edge));

            System.out.println("Removing cycle: " + sourceCat.getTitle() + " - " + targetCat.getTitle());

            createdGraph.removeEdge(edge);
        }
        System.out.println("Graph cycles remove done");
    }
    
    private static DefaultEdge findCycle() {
        colorMap = new HashMap<Integer, Color>();
        // initialize all nodes with white
        for (int cId : createdGraph.vertexSet()) {
            colorMap.put(cId, Color.white);
        }

        for (int cId : createdGraph.vertexSet()) {
            if (colorMap.get(cId).equals(Color.white)) {
                DefaultEdge e = visit(cId);
                if (e != null) {
                    return e;
                }
            }
        }
        return null;
    }
    
    private static DefaultEdge visit(int cId) {
        colorMap.put(cId, Color.grey);
        Set<DefaultEdge> outgoingEdges = createdGraph.outgoingEdgesOf(cId);
        for (DefaultEdge edge : outgoingEdges) {
            int outNode = createdGraph.getEdgeTarget(edge);
            if (colorMap.get(outNode).equals(Color.grey)) {
                return edge;
            }
            else if (colorMap.get(outNode).equals(Color.white)) {
                DefaultEdge e = visit(outNode);
                if (e != null) {
                    return e;
                }
            }
        }
        colorMap.put(cId, Color.black);
        return null;
    }
    
    public static void saveGraph(DirectedGraph<Integer, DefaultEdge> graph, String location) throws IOException {
        File file = new File(location);
        file.createNewFile();
        if(!file.canWrite()) {
            throw new IOException("Cannot write to file: " + location);
        }
        
        BufferedOutputStream fos;
        ObjectOutputStream oos;
        fos = new BufferedOutputStream(new FileOutputStream(file));
        oos = new ObjectOutputStream(fos);
        oos.writeObject(graph);
        oos.close();
    }
        
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

    public static Map<Integer,List<Integer>> getVerticesDepthMap() throws WikiApiException {
        return verticesDepthMap;
    }

    public static Map<Integer, List<Integer>> createVerticesDepthMap(Wikipedia pWiki, DirectedGraph<Integer, DefaultEdge> lGraph) throws WikiApiException {
        loadedGraph = lGraph;
        System.out.println("# of added vertices: "+ loadedGraph.vertexSet().size());
        System.out.println("# of added edges: "+ loadedGraph.edgeSet().size());
        System.out.println("# of leaf vertices: "+ getLeafVertices(loadedGraph).size());
        
        root = pWiki.getMetaData().getMainCategory().getPageId();

        System.out.println("begin computing verticesDepthMap");
        verticesDepthMap = new HashMap<Integer,List<Integer>>();

        List<Integer> verticesQueue = new ArrayList<Integer>();
        
        Set<Integer> leafVertices = getLeafVertices(loadedGraph);
        
        verticesQueue.addAll(leafVertices);

        System.out.println("# of leaf vertices: " + verticesQueue.size());
        fillverticesDepthMap(root, verticesQueue);
        verticesQueue.clear();

        Set<Integer> categoryIds = getCategorieIds();
        
        for (int cId : categoryIds) {
            if (!verticesDepthMap.containsKey(cId)) {
                verticesQueue.add(cId);
            }
        }

        System.out.println(verticesQueue.size() + " non leaf vertices not on a shortest leaf-vertex to root path.");
        fillverticesDepthMap(root, verticesQueue);

        for (int cId : categoryIds) {
            if (!verticesDepthMap.containsKey(cId)) {
                System.out.println("no path for " + cId);
            }
        }

        depth = getDepthFromMap();

        System.out.println("Setting depth of category graph: " + depth);

        return verticesDepthMap;
    }
    
    private static void fillverticesDepthMap(int root, List<Integer> verticesQueue) throws WikiApiException {
        while (!verticesQueue.isEmpty()) {
            int currentVertex = verticesQueue.get(0);
            verticesQueue.remove(0);

            System.out.println("verticesQueue size: " + verticesQueue.size());

            if (getVerticesDepthMap().containsKey(currentVertex)) {
                continue;
            }

            List<Integer> verticesOnPath = getPathToRoot(root, currentVertex);

            if (verticesOnPath == null) {
                getVerticesDepthMap().put(currentVertex, new ArrayList<Integer>());
                continue;
            }

            if (verticesOnPath.get(0) != currentVertex || verticesOnPath.get(verticesOnPath.size()-1) != root) { 
                System.out.println("Something is wrong with the path to the root");
                System.out.println(verticesOnPath.get(0) + " -- " + currentVertex);
                System.out.println(verticesOnPath.get(verticesOnPath.size()-1) + " -- " + root);
                System.out.println("size = {}"+ verticesOnPath.size());
                System.exit(1);
            }

            int i = 0;
            for (int vertex : verticesOnPath) {
                if (getVerticesDepthMap().containsKey(vertex)) {
                    continue;
                }
                else {
                    getVerticesDepthMap().put(vertex, new ArrayList<Integer>(verticesOnPath.subList(i, verticesOnPath.size())));
                }
                i++;
            }
        }
    }
    
    private static List<Integer> getPathToRoot(int root, int currentVertex) throws WikiApiException {
        List<Integer> pathToRoot = new LinkedList<Integer>();

        List<Integer> longestPath = new ArrayList<Integer>();
        traceLongestPath(root, currentVertex, pathToRoot, longestPath);
        if (longestPath.size() == 0) {
            return null;
        }
        else {
            return longestPath;
        }
    }

    private static void traceLongestPath(int root, int currentVertex, List<Integer> currentPath, List<Integer> longestPath) {
        currentPath.add(currentVertex);

        if (currentVertex == root) {
            System.out.println("found root");

            if (longestPath.size() != 0) {
                if (currentPath.size() > longestPath.size()) {
                    System.out.println("setting new longest path");
                    longestPath.clear();
                    longestPath.addAll(currentPath);
                }
            }
            else {
                System.out.println("initializing longest path");
                longestPath.addAll(currentPath);
            }
        }

        if (longestPath.size() != 0 && currentPath.size() < longestPath.size()) {
            return;
        }

        Set<DefaultEdge> incomingEdges = loadedGraph.incomingEdgesOf(currentVertex);
        
        if (incomingEdges == null || incomingEdges.size() == 0) {
            System.out.println("found non-root source");
            return;
        }

        for (DefaultEdge incomingEdge : incomingEdges) {
            Integer sourceVertex = loadedGraph.getEdgeSource(incomingEdge);
            
            if (sourceVertex == currentVertex) {
                System.out.println("Source vertex equals current vertex.");
                System.exit(1);
            }
            List<Integer> savedPath = new LinkedList<Integer>(currentPath);
            traceLongestPath(root, sourceVertex, currentPath, longestPath);
            currentPath.clear();
            currentPath.addAll(savedPath);
        }
        return;
    }
    
    public static void saveMap(Map<?,?> map, String location) throws IOException {
        File file = new File(location);
        file.createNewFile();
        if(!file.canWrite()) {
            throw new IOException("Cannot write to file: " + location);
        }
        
        BufferedOutputStream fos;
        ObjectOutputStream oos;
        fos = new BufferedOutputStream(new FileOutputStream(file));
        oos = new ObjectOutputStream(fos);
        oos.writeObject(map);
        oos.close();
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
    
    private static double getDepthFromMap() throws WikiApiException {
        int max = 0;
        for (List<Integer> path : getVerticesDepthMap().values()) {
            if (path.size() > max) {
                max = path.size();
            }
        }

        max = max - 1;

        if (max < 0) {
            return 0;
        }
        else {
            return max;
        }
    }
    

    public static Set<Integer> getChildren(int pageID) {
        Set<DefaultEdge> outgoingEdges = loadedGraph.outgoingEdgesOf(pageID);
        Set<Integer> outLinks = new HashSet<Integer>();
        for (DefaultEdge edge : outgoingEdges) {
            outLinks.add(loadedGraph.getEdgeTarget(edge));
        }
        return outLinks;
    }

    public static Set<Integer> getParents(int pageID) {
        Set<DefaultEdge> incomingEdges = loadedGraph.incomingEdgesOf(pageID);
        Set<Integer> inLinks = new HashSet<Integer>();
        for (DefaultEdge edge : incomingEdges) {
            inLinks.add(loadedGraph.getEdgeSource(edge));
        }
        return inLinks;
    }
    
    public static Map<Integer, Integer> createDescendantsMap(DirectedGraph<Integer, DefaultEdge> lGraph) throws WikiApiException, IOException, ClassNotFoundException {
        loadedGraph = lGraph;

        descendantsCountMap = new HashMap<Integer,Integer>();

        List<Integer> verticesQueue = new ArrayList<Integer>();
        
        Set<Integer> visitedVertices = new HashSet<Integer>();
        
        Set<Integer> leafVertices = getLeafVertices(loadedGraph);
        
        verticesQueue.addAll(leafVertices);
        System.out.println("# of leaf vertices: " + verticesQueue.size());
        
        while (!verticesQueue.isEmpty()) {
            int currentVertex = verticesQueue.get(0);
            verticesQueue.remove(0);

            if (visitedVertices.contains(currentVertex)) {
                continue;
            }

            Set<Integer> children = getChildren(currentVertex);

            int validChildren = 0;
            int descendantsSum = 0;
            boolean invalid = false;
            for (int child : children) {
                if (loadedGraph.containsVertex(child)) {
                    if (descendantsCountMap.containsKey(child))  {
                        descendantsSum += descendantsCountMap.get(child);
                        validChildren++;
                    }
                    else {
                        invalid = true;
                    }
                }
            }

            if (invalid) {
                verticesQueue.add(currentVertex);
                continue;
            }

            visitedVertices.add(currentVertex);

            int currentVertexDescendantsCount = validChildren + descendantsSum;
            System.out.println("Child id: " + currentVertex + ", Descendants Count: " + currentVertexDescendantsCount);

            descendantsCountMap.put(currentVertex, currentVertexDescendantsCount);
            
            for (int parent : getParents(currentVertex)) {
                if (loadedGraph.containsVertex(parent)) {
                    verticesQueue.add(parent);
                }
            }
        }

        System.out.println("# of visited vertices: " + visitedVertices.size());
        if (visitedVertices.size() != loadedGraph.vertexSet().size()) {
            throw new WikiApiException("# of visited vertices: " + visitedVertices.size() + ", # of Graph vertices: " + loadedGraph.vertexSet().size());
        }
        if (descendantsCountMap.size() != loadedGraph.vertexSet().size()) {
            throw new WikiApiException("descendantsMap size: " + descendantsCountMap.size() + ", # of Graph vertices: " + loadedGraph.vertexSet().size());
        }

        for (int key : descendantsCountMap.keySet()) {
            if (descendantsCountMap.get(key) > loadedGraph.vertexSet().size() || descendantsCountMap.get(key) < 0) {
                descendantsCountMap.put(key, (loadedGraph.vertexSet().size()-1));
            }
        }
    return descendantsCountMap;
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
    
    public static void main(String[] args) throws WikiApiException, IOException, ClassNotFoundException{
        dbConfig = new DatabaseConfiguration();
        
        dbConfig.setHost("localhost");
        dbConfig.setDatabase("wikidump");
        dbConfig.setUser("root");
        dbConfig.setPassword("");
        dbConfig.setLanguage(WikiConstants.Language.arabic);

        wiki = new Wikipedia(dbConfig);
        
        if (!new File(path + categoryGraphFileName).exists()) {
            DirectedGraph<Integer, DefaultEdge> catGraph = constructCategoryGraph(wiki, getCategorieIds());
            saveGraph(catGraph, path + categoryGraphFileName);
            System.out.println("CategoryGraph created and saved successfully");
        }
        
        loadedGraph = loadGraph(path + categoryGraphFileName);
        System.out.println("loadedGraph vertexSet size: " + loadedGraph.vertexSet().size());

        if (!new File(path + verticesDepthMapFileName).exists()) {
            Map<Integer, List<Integer>> vDepthMap = createVerticesDepthMap(wiki,  loadedGraph);
            saveMap(vDepthMap, path + verticesDepthMapFileName);
            System.out.println("VerticesDepthMap created and saved successfully");
        }
        
        if (!new File(path + descendantsMapFileName).exists()) {
            Map<Integer, Integer> vDescendantsCMap = createDescendantsMap(loadedGraph);
            saveMap(vDescendantsCMap, path + descendantsMapFileName);
            System.out.println("vertexDescendantsCountMap created and saved successfully");
        }
    }
}
