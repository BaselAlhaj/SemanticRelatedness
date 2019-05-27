package SemanticRelatedness;

import de.tudarmstadt.ukp.wikipedia.api.Page;
import de.tudarmstadt.ukp.wikipedia.api.WikiConstants;
import de.tudarmstadt.ukp.wikipedia.api.exception.WikiApiException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Basel A. Alhaj
 */

public class ContextRelationMetric implements WikiConstants {

    public static double contextRelation(List<Pair<Integer, Integer>> pairDisambiguationIdsList) throws WikiApiException {
        long startTime = System.currentTimeMillis();
        
        List relatednessValues = new ArrayList<>();

        double contextRelatedness = 0;
        for(Pair pair : pairDisambiguationIdsList){
            Page term1Page = DBConnection.getWiki().getPage((Integer)pair.getL());
            Page term2Page = DBConnection.getWiki().getPage((Integer)pair.getR());

            Set<Integer> p1Links = new HashSet<>();
            Set<Integer> p2Links = new HashSet<>();
            Set<Integer> intersectionXY = new HashSet<>();

            int p1NumberOfLinks = 0;
            int p2NumberOfLinks = 0;

            p1Links = term1Page.getInlinkIDs();
            p2Links = term2Page.getInlinkIDs();

            p1NumberOfLinks = p1Links.size();
            p2NumberOfLinks = p2Links.size();

            int maxXY = 0;
            int minXY = 0;

            if(p1NumberOfLinks > p2NumberOfLinks){
                maxXY = p1NumberOfLinks;
                minXY = p2NumberOfLinks;
            }else{
                maxXY = p2NumberOfLinks;
                minXY = p1NumberOfLinks;
            }

            intersectionXY = Sets.intersection(p1Links, p2Links);
            int intersectionXYSize = intersectionXY.size();

            long numberOfVertices = DBConnection.getWiki().getMetaData().getNumberOfPages();

            if(p1NumberOfLinks == 0){
                contextRelatedness = -1;
                System.out.println("Article \"" + term1Page.getTitle() + "\" has no incoming links");
            }else if(p2NumberOfLinks == 0){
                contextRelatedness = -1;
                System.out.println("Article \"" + term2Page.getTitle() + "\" has no incoming links");
            }else if(intersectionXYSize == 0){
                contextRelatedness = 0;
            }
            else{
                contextRelatedness = (1 - ((Math.log(maxXY) - Math.log(intersectionXYSize)) / (Math.log(numberOfVertices) - Math.log(minXY))));
                if(contextRelatedness < 0){
                    contextRelatedness = 0;
                }
            }
            relatednessValues.add(contextRelatedness);
        }
        
        long duration = System.currentTimeMillis() - startTime;

        System.out.println("Elapsed Time: " + String.format("%02d min, %02d sec",
                TimeUnit.MILLISECONDS.toMinutes(duration),
                TimeUnit.MILLISECONDS.toSeconds(duration) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration))));
        
        if(relatednessValues.size() == 0){
            return -1;
        }
        else{
            return (double) Collections.max(relatednessValues);
        }
    }
}