package SemanticRelatedness;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import de.tudarmstadt.ukp.wikipedia.api.exception.WikiApiException;
import de.tudarmstadt.ukp.wikipedia.api.exception.WikiInitializationException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Basel A. Alhaj
 */
public class ReadCSVFile {

    public static double alpha = 0.5;
    
    public static void readDataLineByLine(String file) 
    { 
        try { 
            FileReader filereader = new FileReader(file); 

            CSVReader csvReader = new CSVReader(filereader); 
            String[] nextRecord; 

            while ((nextRecord = csvReader.readNext()) != null) { 
                for (String cell : nextRecord) { 
                    System.out.print(cell + "\t"); 
                } 
                System.out.println(); 
            } 
        } 
        catch (Exception e) { 
            e.printStackTrace(); 
        } 
    } 
    
    public static void main(String[] args) throws FileNotFoundException, IOException, WikiApiException, WikiInitializationException, ClassNotFoundException{
        DBConnection.setConfiguration(true);
        
        String path = System.getProperty("user.dir") + "\\Dataset.csv";

        FileReader filereader = new FileReader(path);
        CSVReader csvReader = new CSVReader(filereader); 
        List<String[]> allData = csvReader.readAll(); 

        Map<Pair<String, String>,Double> humanJudgeMap = new HashMap<>();

        for(String[] s : allData){
            if(!s[0].equals("Term 1")){
                humanJudgeMap.put(new Pair<String, String>(s[0], s[1]), (Double.parseDouble(s[2])/10));
            }
        }
                                
        File file = new File(System.getProperty("user.dir") + "\\ApproachResults.csv");
        
        FileWriter outputfile = new FileWriter(file); 

        CSVWriter writer = new CSVWriter(outputfile); 
        
        String[] header = { "Term 1", "Term 2", "Human (mean)", "Context-based", "Category-based(CategoryDepthIV)", "Category-based(CategoryDescendantsIV)", "WeightedAverage"}; 
        writer.writeNext(header); 
            
        System.out.println("humanJudgeMap: " + humanJudgeMap.size());
        for (Map.Entry<Pair<String, String>, Double> entry : humanJudgeMap.entrySet()) {
            System.out.println("------------------------------------------------------------------------");
            String[] terms = new String[]{entry.getKey().getL(), entry.getKey().getR()};
            
            //Context-based Relation
            double contextResult = ContextRelationMetric.contextRelation(ArticleMatcher.matcher(terms));
            
            //Category-based Relation - CategoryDepthIV
            double depthIVResult = CategoryRelationMetric.categoryRelation(ArticleMatcher.matcher(terms), 1);
            
            //Category-based Relation - CategoryDescendantsIV
            double descendantsIVResult = CategoryRelationMetric.categoryRelation(ArticleMatcher.matcher(terms), 2);            

            //Cominbed Approach (WeightedAverage)
            double weightedAverageResult = (alpha * contextResult) + ((1 - alpha) * descendantsIVResult);
            
            //add data to csv 
            String[] data1 = { entry.getKey().getL(), 
                entry.getKey().getR(), 
                Double.toString(entry.getValue()), 
                Double.toString(contextResult), 
                Double.toString(depthIVResult), 
                Double.toString(descendantsIVResult), 
                Double.toString(weightedAverageResult)}; 
            writer.writeNext(data1); 

            System.out.println("Key : (" + entry.getKey().getL() + "," + entry.getKey().getR() + ")");
            System.out.println("humanJudge Value:      " + entry.getValue());
            System.out.println("context Value:   " + contextResult);
            System.out.println("categoryDepth Value: " + depthIVResult);
            System.out.println("categoryDescendants Value: " + descendantsIVResult);
            System.out.println("weightedAverage Value: " + weightedAverageResult); 
        }
        //closing writer connection 
        writer.close(); 
    }
    

}
