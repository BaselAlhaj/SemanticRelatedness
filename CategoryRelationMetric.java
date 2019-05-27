package SemanticRelatedness;

import de.tudarmstadt.ukp.wikipedia.api.Category;
import de.tudarmstadt.ukp.wikipedia.api.Page;
import de.tudarmstadt.ukp.wikipedia.api.WikiConstants;
import de.tudarmstadt.ukp.wikipedia.api.exception.WikiApiException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jgrapht.DirectedGraph;
import org.jgrapht.graph.DefaultEdge;

/**
 *
 * @author Basel A. Alhaj
 */

public class CategoryRelationMetric implements WikiConstants {
    private static final Set<Integer> allAncestorsIds = new HashSet<Integer>();
    private static final Integer maxAncestors = 30;
    private static Integer commonAncestor = 0;
    
    public static DirectedGraph<Integer, DefaultEdge> catGraph = DBConnection.getGraph();
    
    public static int iterableSize(Iterable iter){
        int size = 0;
        if (iter instanceof Collection) {
            size = ((Collection<?>) iter).size();
        }
        return size;
    }
    
    public static <E> Collection<E> makeCollection(Iterable<E> iter) {
        Collection<E> list = new ArrayList<E>();
        for (E item : iter) {
            list.add(item);
        }
        return list;
    }

    public static int getLCA(int categoryId1, int categoryId2) throws WikiApiException {
        if (categoryId1 == categoryId2) {
            return categoryId1;
        }

        List<Integer> vertexList1 = DBConnection.getVerticesDepthMap().get(categoryId1);
        List<Integer> vertexList2 = DBConnection.getVerticesDepthMap().get(categoryId2);

        if (vertexList1 == null || vertexList2 == null || vertexList1.size() == 0 || vertexList2.size() == 0) {
            System.out.println("One of the vertex lists is null or empty!");

            return -1;
        }

        for (int tmpVertex2 : vertexList2) {
            if (tmpVertex2 == categoryId1) {
                return categoryId1;
            }
        }

        for (int tmpVertex1 : vertexList1) {
            if (tmpVertex1 == categoryId2) {
                return categoryId2;
            }
        }
        
        for (int tmpVertex1 : vertexList1) {
            for (int tmpVertex2 : vertexList2) {
                if (tmpVertex1 == tmpVertex2) {
                    return tmpVertex1;
                }
            }
        }

        System.out.println("No LCA found");

        return -1;
    }
    
    public static double getCategoryDescendantsInformationValue(Category category) throws WikiApiException {
        int vertex = category.getPageId();

        long descendantsCount  = DBConnection.getLoadedVDescendantsMap().get(vertex);
        int numberOfVertices = DBConnection.getGraph().vertexSet().size();

        if (descendantsCount > numberOfVertices) {
            throw new WikiApiException("Something is wrong with the hyponymCountMap " + descendantsCount + " hyponyms but only " + numberOfVertices + " Vertices");
        }

        double categoryDescendantsIV = 1;
        if (descendantsCount > 0) {
            categoryDescendantsIV = (1 - ( Math.log(descendantsCount) / Math.log(numberOfVertices)));
        }
        return categoryDescendantsIV;
    }
    
    public static double getCategoryDepthInformationValue(Category category) throws WikiApiException {
        int cDepth  = DBConnection.getVerticesDepthMap().get(category.getPageId()).size();
        double maxDepth = DBConnection.getLoadedGraphDepth();
        
        if (cDepth > maxDepth) {
            throw new WikiApiException("Something is wrong with the cDepth " + cDepth + " vertices but only " + maxDepth + " Vertices");
        }

        double categoryDepthIV = 0;
        if (cDepth > 0) {
            categoryDepthIV = (Math.log((cDepth)) / Math.log(maxDepth));
        }
        return categoryDepthIV;
    }
    
    public static double categoryRelation(List<Pair<Integer, Integer>> pairDisambiguationIdsList, int metric) throws WikiApiException {
        long startTime = System.currentTimeMillis();
         
        List relatednessValues = new ArrayList<>();

        double groupwiseRelatedness = 0;
        for(Pair pair : pairDisambiguationIdsList){
            Page term1Page = DBConnection.getWiki().getPage((Integer)pair.getL());
            Page term2Page = DBConnection.getWiki().getPage((Integer)pair.getR());

            Set<Category> p1Categories = term1Page.getCategories();
            int p1CategoriesSize =  p1Categories.size();
            Set<Category> p2Categories = term2Page.getCategories();
            int p2CategoriesSize =  p2Categories.size();

            List<Pair<Category, Category>> pairCateogriesList = new ArrayList<Pair<Category, Category>>();

            for(Category c1 : p1Categories){
                for(Category c2 : p2Categories){
                    pairCateogriesList.add(new Pair<Category, Category>(c1,c2));
                }
            }

            Map<Integer, Double> p1Map = new HashMap<Integer, Double>();
            Map<Integer, Double> p2Map = new HashMap<Integer, Double>();

            for(Pair cPair : pairCateogriesList){
                Category c1 = (Category) cPair.getL();
                Category c2 = (Category) cPair.getR();

                int lcaId = getLCA(c1.getPageId(), c2.getPageId());
                Category lca;

                if(lcaId == -1){
                    lca = DBConnection.getWiki().getMetaData().getMainCategory();
                }else{
                    lca = DBConnection.getWiki().getCategory(lcaId);
                }

                double c1_IC = 0;
                double c2_IC = 0;
                double lca_IC = 0;
                if(metric == 1){
                    c1_IC = getCategoryDepthInformationValue(c1);
                    c2_IC = getCategoryDepthInformationValue(c2);
                    lca_IC = getCategoryDepthInformationValue(lca);
                }else if(metric == 2){
                    c1_IC = getCategoryDescendantsInformationValue(c1);
                    c2_IC = getCategoryDescendantsInformationValue(c2);
                    lca_IC = getCategoryDescendantsInformationValue(lca);
                }            

                double pairwise = 0;
                if(c1.getPageId() == c2.getPageId()){
                    pairwise = 1;
                }else{
                    if((c1_IC + c2_IC) > 0){
                        pairwise = lca_IC / (c1_IC + c2_IC);
                    }
                }

                if(pairwise != Double.POSITIVE_INFINITY){
                    if(p1Map.containsKey(c1.getPageId())){
                        if(p1Map.get(c1.getPageId()) < pairwise){
                            p1Map.replace(c1.getPageId(), pairwise);
                        }
                    }
                    else{
                        p1Map.put(c1.getPageId(), pairwise);
                    }

                    if(p2Map.containsKey(c2.getPageId())){
                        if(p2Map.get(c2.getPageId()) < pairwise){
                            p2Map.replace(c2.getPageId(), pairwise);
                        }
                    }   
                    else{
                        p2Map.put(c2.getPageId(), pairwise);
                    }
                }
            }

            double sumBestC1 = 0;
            for(Category c : p1Categories){
                if(p1Map.get(c.getPageId()) != null){
                    sumBestC1 += p1Map.get(c.getPageId());
                }
            }

            double sumBestC2 = 0;
            for(Category c : p2Categories){
                if(p2Map.get(c.getPageId()) != null){
                    sumBestC2 += p2Map.get(c.getPageId());
                }
            }

            groupwiseRelatedness = (0.5 * (sumBestC1 / p1Categories.size())) + (0.5 * (sumBestC2 / p2Categories.size()));
            relatednessValues.add(groupwiseRelatedness);
        }
        
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("Elapsed Time: " + String.format("%02d min, %02d sec",
                TimeUnit.MILLISECONDS.toMinutes(duration),
                TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))));
        
        return (double) Collections.max(relatednessValues);
    }
}