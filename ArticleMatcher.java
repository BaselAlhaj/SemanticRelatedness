package SemanticRelatedness;

import de.tudarmstadt.ukp.wikipedia.api.Page;
import de.tudarmstadt.ukp.wikipedia.api.exception.WikiApiException;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JOptionPane;

/**
 *
 * @author Basel A. Alhaj
 */
public class ArticleMatcher {

    private static String decodeTitleWikistyle(String pTitle) {
        return pTitle.replace('_', ' ');
    }

    private static String encodeTitleWikistyle(String pTitle) {
        return pTitle.replace(' ', '_');
    }

    private static List<String> parseTitle(String titleText){
        String regexFindParts = "(.*?)[ _]\\((.+?)\\)$";

        Pattern pattern = Pattern.compile(regexFindParts);
        Matcher matcher = pattern.matcher(decodeTitleWikistyle(titleText));

        String mainText = null;
        String disambiguationText = null;

        if (matcher.find()) {
            mainText = matcher.group(1);
            disambiguationText = matcher.group(2);

            String relevantTitleParts = mainText + " (" + disambiguationText + ")";           
        }

        return Arrays.asList(mainText, disambiguationText);
    }
    
    private static String titleRegexMatcher(String pTitle) {
        Map<String, String> arPatternsReplace = new HashMap<>();
        arPatternsReplace.put("ا|إ|أ|آ", "(ا|إ|أ|آ)");
        arPatternsReplace.put("ه$|ة$", "(ه|ة)");
        arPatternsReplace.put("ي$|ى$", "(ي|ى)");
        
        StringTokenizer tokens = new StringTokenizer(pTitle, " "); 
        String searchText = "";
        while(tokens.hasMoreTokens()){
            String t = tokens.nextToken();
            for(String key : arPatternsReplace.keySet()){
                t = t.replaceAll(key, arPatternsReplace.get(key));
            }
            searchText = searchText + " " + t;
        }

        return searchText.trim();
    }

    public static Integer getMainPageId(Integer id){
        Integer pageId = null;
        try{  
            Statement stmt = DBConnection.getConnection().createStatement();  
            ResultSet rs = stmt.executeQuery("select r.pageid from redirects as r where r.rd_from =" + id);  
            while(rs.next()) {
                pageId = rs.getInt(1);
            }
        }catch(Exception e){ System.out.println(e);}  

        return pageId;
    }
    
    public static boolean isDisambiguation(Integer id){
        boolean isDisambiguation = false;
        try{  
            Statement stmt = DBConnection.getConnection().createStatement();  
            ResultSet rs = stmt.executeQuery("SELECT isDisambiguation FROM `page` WHERE pageid =" + id);
            while(rs.next()) {
                if(rs.getInt(1) == 1){
                    isDisambiguation = true;
                }
            } 
        }catch(Exception e){ System.out.println(e);}  
    
        return isDisambiguation;
    }
    
    public static boolean existsPageExact(String pTitle) {
        if (pTitle == null || pTitle.length() == 0) {
            return false;
        }
        boolean result = false;
        try{  
            Statement stmt = DBConnection.getConnection().createStatement();  
            ResultSet rs = stmt.executeQuery("SELECT * FROM `pagemapline` WHERE name = '"+ encodeTitleWikistyle(pTitle) + "'"); // LIMIT 1 
            result = rs.first();
        }catch(Exception e){ System.out.println(e);}  
        return result;
    }
    
    public static boolean existsPage(String pTitle) {
        if (pTitle == null || pTitle.length() == 0) {
            return false;
        }
        
        boolean result = false;
        try{  
            Statement stmt = DBConnection.getConnection().createStatement();  
            System.out.println(encodeTitleWikistyle(titleRegexMatcher(pTitle)));
            ResultSet rs = stmt.executeQuery("SELECT * FROM `pagemapline` WHERE name REGEXP '^"+  encodeTitleWikistyle(titleRegexMatcher(pTitle)) + "$'"); // LIMIT 1 
            result = rs.first();
        }catch(Exception e){ System.out.println(e);}  
        return result;
    }
    
    public static Integer getCorrespondingPageId(String pTitle){
        
        Integer pageId = null;
        try{  
            Statement stmt = DBConnection.getConnection().createStatement();  
            ResultSet rs = stmt.executeQuery("SELECT * FROM `pagemapline` WHERE name REGEXP '^"+  encodeTitleWikistyle(titleRegexMatcher(pTitle)) + "$' LIMIT 1 ");
            while(rs.next()) {
                pageId = rs.getInt(1);
            }
        }catch(Exception e){ System.out.println(e);}  

        return pageId;
    }
    
    public static Set<Integer> getPageDisambiguationIds(String title){
        String mainTitle = parseTitle(title).get(0);
        System.out.println("mainTitle: " + mainTitle); 
        Set<Integer> disambiguationIds = new HashSet<>();
        try{ 
            Statement stmt = DBConnection.getConnection().createStatement();  
            
            String sql = "SELECT pageid FROM `page` WHERE isDisambiguation != 1 AND name LIKE '" + encodeTitleWikistyle(mainTitle) + "_(%'";
            ResultSet rs = stmt.executeQuery(sql);

            while(rs.next()) {
                int pageId = rs.getInt(1);
                Integer correspondingId = getMainPageId(pageId);
                if(correspondingId != null){
                    disambiguationIds.add(correspondingId);
                }else{
                    disambiguationIds.add(pageId);
                }
            }
        }catch(Exception e){ System.out.println(e);}  
    
        return disambiguationIds;
    }
    
    public static Integer getArticleWordsCount(Integer id){
        int count = 0;
        try{ 
            Statement stmt = DBConnection.getConnection().createStatement();  

            String sql = ("SELECT text FROM `page` WHERE pageid =" + id);
            ResultSet rs = stmt.executeQuery(sql);
            
            while(rs.next()) {
                String text = rs.getString(1);
                String [] words = text.split(" ");
                count = words.length;
            }
        }catch(Exception e){ System.out.println(e);}  
    
        return count;
    }
    
    public static List<Pair<Integer, Integer>> matcher(String[] terms) throws WikiApiException{
        Page term1Page = DBConnection.getWiki().getPage(getCorrespondingPageId(terms[0]));
        Page term2Page = DBConnection.getWiki().getPage(getCorrespondingPageId(terms[1]));
        
        Integer Page1Id = getMainPageId(term1Page.getPageId());
        if(Page1Id != null){
            term1Page = DBConnection.getWiki().getPage(Page1Id);
        } 
        
        Integer Page2Id = getMainPageId(term2Page.getPageId());
        if(Page2Id != null){
            term2Page = DBConnection.getWiki().getPage(Page2Id);
        }
        
        Set<Integer> p1DisambiguationIds = new HashSet<>();
        Set<Integer> p2DisambiguationIds = new HashSet<>();
        int p1DisambiguationIdsSize = 0;
        int p2DisambiguationIdsSize = 0;
        
        if(isDisambiguation(term1Page.getPageId())){
            p1DisambiguationIds.addAll(getPageDisambiguationIds(term1Page.getTitle().toString()));
            p1DisambiguationIdsSize =  p1DisambiguationIds.size();
        }else{
            p1DisambiguationIds.add(term1Page.getPageId());
            p1DisambiguationIdsSize =  p1DisambiguationIds.size();
        }
        
        if(isDisambiguation(term2Page.getPageId())){
            p2DisambiguationIds.addAll(getPageDisambiguationIds(term2Page.getTitle().toString()));
            p2DisambiguationIdsSize =  p2DisambiguationIds.size();
        }else{
            p2DisambiguationIds.add(term2Page.getPageId());
            p2DisambiguationIdsSize =  p2DisambiguationIds.size();
        }
        
        List<Pair<Integer, Integer>> pairDisambiguationIdsList = new ArrayList<Pair<Integer, Integer>>();

        for(Integer id1 : p1DisambiguationIds){
            for(Integer id2 : p2DisambiguationIds){
                pairDisambiguationIdsList.add(new Pair<Integer, Integer>(id1, id2));
            }
        }
        
        if (p1DisambiguationIdsSize == 0){
            JOptionPane.showMessageDialog(null, "There is no corresponding page in Wikipedia to '" + terms[0] + "'", "InfoBox: " + "Info", JOptionPane.INFORMATION_MESSAGE);
        }else if (p2DisambiguationIdsSize == 0){
            JOptionPane.showMessageDialog(null, "There is no corresponding page in Wikipedia to '" + terms[1] + "'", "InfoBox: " + "Info", JOptionPane.INFORMATION_MESSAGE);
        }
        return pairDisambiguationIdsList;
    }
}
