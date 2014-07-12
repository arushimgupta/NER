/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package svm;

import edu.emory.mathcs.backport.java.util.Collections;
import edu.stanford.nlp.util.Pair;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.TreeMap;
import jsafran.Dep;
import jsafran.DetGraph;
import jsafran.GraphIO;
import jsafran.MateParser;
import jsafran.POStagger;

import lex.Dependency;
import lex.Lemma;
import lex.Segment;
import lex.Utterance;
import lex.Word;
import tools.CNConstants;
import utils.ErrorsReporting;
import org.apache.commons.io.FileUtils;
import tools.IntegerValueComparator;

/**
 *
 * @author rojasbar
 */
public class AnalyzeSVMClassifier {
    public static String MODELFILE="en.%S.treek.mods";
    public static String TRAINFILE="groups.%S.tab.treek.train";
    public static String TESTFILE="groups.%S.tab.treek.test";
    public static String LISTTRAINFILES="esterParseTrainALL.xmll";
    public static String LISTTESTFILES="esterParseTestALL.xmll";
    public static String UTF8_ENCODING="UTF8";
    public static String PROPERTIES_FILE="streek.props";
    public static String NUMFEATSINTRAINFILE="2-";
    public static String ONLYONEPNOUNCLASS=CNConstants.PRNOUN;
    public static String[] groupsOfNE = {CNConstants.PERS,CNConstants.ORG, CNConstants.LOC, CNConstants.PROD};
    public static int TRAINSIZE= Integer.MAX_VALUE;

    private HashMap<String,Integer> dictFeatures=new HashMap<>();

    public AnalyzeSVMClassifier(){

    }
    public void saveFilesForLClassifierWords(String en, boolean istrain, boolean onlyVector) {
            try {

                GraphIO gio = new GraphIO(null);
                OutputStreamWriter outFile =null;
                String xmllist=LISTTRAINFILES;
                if(istrain){
                    String fname=(onlyVector)?TRAINFILE.replace("%S", en).replace("treek", "vector"):TRAINFILE.replace("%S", en);
                    outFile = new OutputStreamWriter(new FileOutputStream(fname),UTF8_ENCODING);
                }else{
                    xmllist=LISTTESTFILES;
                     String fname=(onlyVector)?TESTFILE.replace("%S", en).replace("treek", "vector"):TESTFILE.replace("%S", en);
                    outFile = new OutputStreamWriter(new FileOutputStream(fname),UTF8_ENCODING);
                }
                BufferedReader inFile = new BufferedReader(new FileReader(xmllist));
                int uttCounter=0;
                for (;;) {
                    String filename = inFile.readLine();
                    if (filename==null) break;
                    List<DetGraph> gs = gio.loadAllGraphs(filename);
                    List<Utterance> utts= new ArrayList<>();
                    for (int i=0;i<gs.size();i++) {
                            DetGraph group = gs.get(i);
                            int nexinutt=0;
                            //outFile.append("NO\tBS\tBS\n");
                            
                            Utterance utt= new Utterance();
                            utt.setId(new Long(uttCounter+1));                            
                            List<Word> words= new ArrayList<>();
                            for (int j=0;j<group.getNbMots();j++) {
                                    nexinutt++;

                                    // calcul du label
                                    int lab = CNConstants.INT_NULL;
                                    int[] groups = group.getGroups(j);
                                    if (groups!=null)
                                        for (int gr : groups) {
                                            
                                            if(en.equals(ONLYONEPNOUNCLASS)){
                                                //all the groups are proper nouns pn
                                                for(String str:groupsOfNE){
                                                    if (group.groupnoms.get(gr).startsWith(str)) {
                                                        lab=1;
                                                        break;
                                                    }
                                                }
                                            }else{
                                                if (group.groupnoms.get(gr).startsWith(en)) {
                                                    //int debdugroupe = group.groups.get(gr).get(0).getIndexInUtt()-1;
                                                    //if (debdugroupe==j) lab = en+"B";    
                                                    //else lab = en+"I";
                                                    lab=1;
                                                    break;
                                                }
                                            }
                                        }
                                    /*        
                      
                                    if(!isStopWord(group.getMot(j).getPOS())){
					String inWiki ="F";
                                        if(!group.getMot(j).getPOS().startsWith("PRO") && !group.getMot(j).getPOS().startsWith("ADJ"))
                                            inWiki =(WikipediaAPI.processPage(group.getMot(j).getForme()).equals(CNConstants.CHAR_NULL))?"F":"T";
                                        outFile.append(lab+"\t"+group.getMot(j).getForme()+"\t"+group.getMot(j).getPOS()+"\t"+ inWiki +"\n");
                                    } 
                                     */                                  
                                    Word word = new Word(j,group.getMot(j).getForme());
                                    word.setPOSTag(group.getMot(j).getPOS(), group.getMot(j).getLemme());
                                    word.setLabel(lab);
                                    word.setUtterance(utt);
                                    words.add(word);
                                    
                                    if(!dictFeatures.containsKey(word.getContent()))
                                        dictFeatures.put(word.getContent(), dictFeatures.size()+1);
                                    
                                    if(!dictFeatures.containsKey(word.getPosTag().getName()))
                                        dictFeatures.put(word.getPosTag().getName(), dictFeatures.size()+1);
                                    
                                    if(!dictFeatures.containsKey(word.getLexicalUnit().getPattern()))
                                        dictFeatures.put(word.getLexicalUnit().getPattern(), dictFeatures.size()+1);                                    
                                    
                                    
                                        
                            }
                            uttCounter++;
                            utt.setWords(words);
                            utt.getSegment().computingWordFrequencies();
                            
                            //extracting the dependency tree from jsafran to our own format
                            for(int d=0;d<group.deps.size();d++){
                                Dep dep = group.deps.get(d);
                                int headidx=dep.head.getIndexInUtt()-1;
                                Word       head = words.get(headidx);
                                int depidx=dep.gov.getIndexInUtt()-1;
                                Word       governor = words.get(depidx);
                                //Assign the dependencies
                                Dependency dependency;
                                if(!utt.getDepTree().containsHead(head)){
                                    dependency = new Dependency(head);
                                }else{
                                    dependency = utt.getDepTree().getDependency(head);
                                }
                                dependency.addDependent(governor, dep.toString());
                                utt.addDependency(dependency);                            
                            }
                            //sets the roots
                            utt.getDepTree().getDTRoot();
                            utts.add(utt);

                    }
                    for(Utterance utt:utts){
                            /** 
                             * built the tree and vector features here
                             */
                            System.out.println("processing utterance:"+utt);
                            //iterate again through words
                            
                    
                            
                            for(Word word:utt.getWords()){
                                
                                int posid=dictFeatures.get(word.getPosTag().getName());
                                int wsid= dictFeatures.get(word.getLexicalUnit().getPattern());
                                String vector=posid+":1 "+wsid+":1";
                                if(posid>wsid)
                                    vector=wsid+":1 "+posid+":1";

                                //Print the word form just to make the debug easy*/
                                //outFile.append(word.getLabel()+"\t"+word.getContent()+" "+tree.trim()+" "+vector+"\n");
                                //outFile.append(word.getLabel()+"\t"+tree.trim()+" "+vector+"\n");
                                if(onlyVector)
                                    outFile.append(word.getLabel()+"\t"+vector+"\n");
                                else{
                                    String tree= utt.getDepTree().getTreeTopDownFeatureForHead(word);
                                    tree=(tree.contains("("))?"|BT| "+ tree+ " |ET|":"|BT| |ET|";
                                    String treeBUp=utt.getDepTree().getTreeBottomUpFeatureForHead(word,"");
                                    treeBUp=(treeBUp.contains("("))?" |BT| ("+ treeBUp + ") |ET|":"|BT| |ET|";
                                    tree=tree.trim()+ treeBUp.trim();    
                                    outFile.append(word.getLabel()+"\t"+tree.trim()+" "+vector+"\n");
                                }
                            }   
                        
                    }
                    if(istrain && uttCounter> TRAINSIZE){
                        break;
                    }                    
                }
                outFile.flush();
                outFile.close();
                inFile.close();
                ErrorsReporting.report("groups saved in groups.*.tab"+uttCounter);
            } catch (IOException e) {
                    e.printStackTrace();
            }
    }   
    /*
     * Syntactic driven spans
     */
    public void saveFilesForLClassifierSpans(String en, boolean onlyVector) {
            try {
                
                

                int totalOfspans=0;
                
                GraphIO gio = new GraphIO(null);
                OutputStreamWriter outFile =null;
                String xmllist=LISTTRAINFILES;
                
                String fname=(onlyVector)?TRAINFILE.replace("%S", en).replace("treek", "spvector"):TRAINFILE.replace("%S", en).replace("treek", "sptk");
                outFile = new OutputStreamWriter(new FileOutputStream(fname),UTF8_ENCODING);
                
                BufferedReader inFile = new BufferedReader(new FileReader(xmllist));
                int uttCounter=0;
                for (;;) {
                    String filename = inFile.readLine();
                    if (filename==null) break;
                    List<DetGraph> gs = gio.loadAllGraphs(filename);
                    List<Utterance> utts= new ArrayList<>();
                    for (int i=0;i<gs.size();i++) {
                            DetGraph group = gs.get(i);
                            int nexinutt=0;
                            //outFile.append("NO\tBS\tBS\n");
                            
                            Utterance utt= new Utterance();
                            utt.setId(new Long(uttCounter+1));                            
                            List<Word> words= new ArrayList<>();
                            HashMap<Pair,String> entitySpans = new HashMap<>();
                            for (int j=0;j<group.getNbMots();j++) {
                                    nexinutt++;

                                    // calcul du label
                                    int lab = CNConstants.INT_NULL;
                                    int[] groups = group.getGroups(j);
                                    if (groups!=null)
                                        for (int gr : groups) {
                                            
                                            if(en.equals(ONLYONEPNOUNCLASS)){
                                                //all the groups are proper nouns pn
                                                for(String str:groupsOfNE){
                                                    if (group.groupnoms.get(gr).startsWith(str)) {
                                                        int stgroup = group.groups.get(gr).get(0).getIndexInUtt()-1;
                                                        int endgroup = group.groups.get(gr).get(group.groups.get(gr).size()-1).getIndexInUtt()-1;
                                                        Pair pair = new Pair(new Integer(stgroup), new Integer(endgroup));
                                                        entitySpans.put(pair, en);
                                                        lab=1;                                                       
                                                        break;
                                                    }
                                                }
                                            }else{
                                                if (group.groupnoms.get(gr).startsWith(en)) {
                                                    int stgroup = group.groups.get(gr).get(0).getIndexInUtt()-1;
                                                    int endgroup = group.groups.get(gr).get(group.groups.get(gr).size()-1).getIndexInUtt()-1;
                                                    Pair pair = new Pair(new Integer(stgroup), new Integer(endgroup));
                                                    List<Pair> pairs=new ArrayList<>();
                                                    entitySpans.put(pair, en);
                                                    //else lab = en+"I";
                                                    lab=1;
                                                    break;
                                                }
                                            }
                                        }
                                    /*        
                      
                                    if(!isStopWord(group.getMot(j).getPOS())){
					String inWiki ="F";
                                        if(!group.getMot(j).getPOS().startsWith("PRO") && !group.getMot(j).getPOS().startsWith("ADJ"))
                                            inWiki =(WikipediaAPI.processPage(group.getMot(j).getForme()).equals(CNConstants.CHAR_NULL))?"F":"T";
                                        outFile.append(lab+"\t"+group.getMot(j).getForme()+"\t"+group.getMot(j).getPOS()+"\t"+ inWiki +"\n");
                                    } 
                                     */                                  
                                    Word word = new Word(j,group.getMot(j).getForme());
                                    word.setPOSTag(group.getMot(j).getPOS(), group.getMot(j).getLemme());
                                    word.setLabel(lab);
                                    word.setUtterance(utt);
                                    words.add(word);
                                    
                                    if(!dictFeatures.containsKey(word.getContent()))
                                        dictFeatures.put(word.getContent(), dictFeatures.size()+1);
                                    
                                    if(!dictFeatures.containsKey(word.getPosTag().getName()))
                                        dictFeatures.put(word.getPosTag().getName(), dictFeatures.size()+1);
                                    
                                    if(!dictFeatures.containsKey(word.getLexicalUnit().getPattern()))
                                        dictFeatures.put(word.getLexicalUnit().getPattern(), dictFeatures.size()+1);         
                            }
                            uttCounter++;
                            utt.setWords(words);
                            
                            
                            //extracting the dependency tree from jsafran to our own format
                            for(int d=0;d<group.deps.size();d++){
                                Dep dep = group.deps.get(d);
                                int headidx=dep.head.getIndexInUtt()-1;
                                Word       head = words.get(headidx);
                                int depidx=dep.gov.getIndexInUtt()-1;
                                Word       governor = words.get(depidx);
                                //Assign the dependencies
                                Dependency dependency;
                                if(!utt.getDepTree().containsHead(head)){
                                    dependency = new Dependency(head);
                                }else{
                                    dependency = utt.getDepTree().getDependency(head);
                                }
                                dependency.addDependent(governor, dep.toString());
                                utt.addDependency(dependency);                            
                            }
                            //sets the roots
                            utt.getDepTree().getDTRoot();
                            utts.add(utt);
                            //add the gold spans
                            for(Pair pair:entitySpans.keySet()){
                                int start = ((Integer) pair.first).intValue();
                                int end = ((Integer) pair.second).intValue();
                                Segment span = new Segment(words.subList(start, end+1));
                                span.computingWordFrequencies();
                                utt.addEntitySpan(entitySpans.get(pair), span);
                                
                            }
                    }
                    for(Utterance utt:utts){
                        /** 
                         * built the tree and vector features here
                         */
                        System.out.println("processing utterance:"+utt);
                        //find all possible spans according to dependency trees                        

                        List<Word> roots = utt.getDepTree().getDTRoot();
                        HashMap<Segment,String> neSegs = utt.getGoldEntities();
                        HashMap<Segment,Integer> outSegs = new HashMap<>();                        
                        
                        if(utt.toString().contains("bonjour"))
                            System.out.println("Entro");
                        for(Word head:roots){
                            List<Word> words=new ArrayList();
                            words.add(head);
                            Segment rootWordSeg= new Segment(words);
                            Segment headSegment = utt.getDepTree().getWholeSegmentRootedBy(utt,rootWordSeg);

                            //headSegment.computingWordFrequencies();
                            int label=-1;
                            if(outSegs.keySet().toString().contains(headSegment.toString()))
                                continue;
                            HashMap<Segment,Integer> tmpOutSegs = new HashMap<>(outSegs);
                            if(!utt.isEntitySpan(headSegment)){          
                                List<Segment> entSegs=new ArrayList<>(neSegs.keySet());
                                List<Segment> segs= headSegment.difference(entSegs);

                               for(Segment seg:segs){
                                   boolean segFound=false;
                                    for(Segment oSeg:outSegs.keySet()){
                                        if(oSeg.contains(seg)){
                                          segFound=true;
                                          break;
                                        }
                                        if(seg.contains(oSeg))
                                            tmpOutSegs.remove(oSeg);                                             
                                    }
                                    if(!segFound)
                                        tmpOutSegs.put(seg,label);
                                }
                               outSegs=tmpOutSegs;


                            } 

                                 
                                
                            }
                            //compute frequencies per span
                            List<Segment> allSegs = new ArrayList<>();                          
                            allSegs.addAll(neSegs.keySet());
                            allSegs.addAll(outSegs.keySet());
                            Collections.sort(allSegs, new Comparator<Segment>() {
                            @Override
                            public int compare(Segment s1, Segment s2)
                            {
                                    return s1.getStart()-s2.getStart();
                            }});  

                            for(Segment seg:allSegs){
                                seg.computingWordFrequencies();
                                int label = CNConstants.INT_NULL;
                                if(neSegs.containsKey(seg))
                                    label = 1;
                                
                                //Feature extraction
                                String vector="";
                                String bow="";
                                HashMap<Integer,Integer> vals= new HashMap<>();
                                for(Word w:seg.getWords()){
                                    bow+=w.getContent()+" ";
                                }

                                for(String pos:seg.getPOSFrequencies().keySet()){
                                    int posid=dictFeatures.get(pos);
                                    vals.put(posid,seg.getPOSFrequency(pos)); 
                                } 

                                for(String ws:seg.getWordShapeFrequency().keySet()){
                                    int wsid= dictFeatures.get(ws);
                                    vals.put(wsid,seg.getWordShapeFrequency(ws));
                                }
                                    
                                List<Integer> keys= new ArrayList(vals.keySet());
                                Collections.sort(keys);
                                for(Integer key:keys){
                                    vector+= key+":"+vals.get(key)+" ";
                                }
                                if(onlyVector)
                                    outFile.append(label+"\t"+vector.trim()+"\n");
                                else{
                                    String tree= utt.getDepTree().getTreeTopDownFeatureForHead(utt.getDepTree().getDTRoot(seg));
                                    tree=(tree.contains("("))?"|BT| "+ tree+ " |ET|":"|BT| |ET|";
                                    //String treeBUp=utt.getDepTree().getTreeBottomUpFeatureForHead(word,"");
                                    //treeBUp=(treeBUp.contains("("))?" |BT| ("+ treeBUp + ") |ET|":"";
                                    tree=tree.trim(); //+ treeBUp.trim();    
                                    //outFile.append(label+"\t"+bow.trim()+" "+tree.trim()+" "+vector.trim()+"\n");
                                    outFile.append(label+"\t"+tree.trim()+" "+vector.trim()+"\n");
                                }   
                                totalOfspans++;
                              }     
                              

                    }
                    
                    if(uttCounter> TRAINSIZE){
                        break;
                    }                    
                }
                outFile.flush();
                outFile.close();
                inFile.close();
                ErrorsReporting.report("groups saved in groups.*.tab || utterances:"+uttCounter+" spans: "+totalOfspans);
            } catch (IOException e) {
                    e.printStackTrace();
            }
    }   
    
    public void saveFilesForClassifierAllSpans(String en,boolean istrain, boolean onlyVector) {
            try {

                int totalOfspans=0;
                
                GraphIO gio = new GraphIO(null);
                OutputStreamWriter outFile =null;
                String fname="";
                String xmllist=LISTTRAINFILES;
                if(istrain)
                    fname=(onlyVector)?TRAINFILE.replace("%S", en).replace("treek", "spvector"):TRAINFILE.replace("%S", en).replace("treek", "asptk");    
                else{
                    xmllist=LISTTESTFILES;
                    fname=(onlyVector)?TESTFILE.replace("%S", en).replace("treek", "spvector"):TESTFILE.replace("%S", en).replace("treek", "asptk"); 
                }
                outFile = new OutputStreamWriter(new FileOutputStream(fname),UTF8_ENCODING);
                BufferedReader inFile = new BufferedReader(new FileReader(xmllist));
                int uttCounter=0;
                for (;;) {
                    String filename = inFile.readLine();
                    if (filename==null) break;
                    List<DetGraph> gs = gio.loadAllGraphs(filename);
                    List<Utterance> utts= new ArrayList<>();
                    for (int i=0;i<gs.size();i++) {
                            DetGraph group = gs.get(i);
                            int nexinutt=0;
                            //outFile.append("NO\tBS\tBS\n");
                            
                            Utterance utt= new Utterance();
                            utt.setId(new Long(uttCounter+1));                            
                            List<Word> words= new ArrayList<>();
                            HashMap<Pair,String> entitySpans = new HashMap<>();
                            for (int j=0;j<group.getNbMots();j++) {
                                    nexinutt++;

                                    // calcul du label
                                    int lab = CNConstants.INT_NULL;
                                    int[] groups = group.getGroups(j);
                                    if (groups!=null)
                                        for (int gr : groups) {
                                            
                                            if(en.equals(ONLYONEPNOUNCLASS)){
                                                //all the groups are proper nouns pn
                                                for(String str:groupsOfNE){
                                                    if (group.groupnoms.get(gr).startsWith(str)) {
                                                        int stgroup = group.groups.get(gr).get(0).getIndexInUtt()-1;
                                                        int endgroup = group.groups.get(gr).get(group.groups.get(gr).size()-1).getIndexInUtt()-1;
                                                        Pair pair = new Pair(new Integer(stgroup), new Integer(endgroup));
                                                        entitySpans.put(pair, en);
                                                        lab=1;                                                       
                                                        break;
                                                    }
                                                }
                                            }else{
                                                if (group.groupnoms.get(gr).startsWith(en)) {
                                                    int stgroup = group.groups.get(gr).get(0).getIndexInUtt()-1;
                                                    int endgroup = group.groups.get(gr).get(group.groups.get(gr).size()-1).getIndexInUtt()-1;
                                                    Pair pair = new Pair(new Integer(stgroup), new Integer(endgroup));
                                                    List<Pair> pairs=new ArrayList<>();
                                                    entitySpans.put(pair, en);
                                                    //else lab = en+"I";
                                                    lab=1;
                                                    break;
                                                }
                                            }
                                        }
                                    /*        
                      
                                    if(!isStopWord(group.getMot(j).getPOS())){
					String inWiki ="F";
                                        if(!group.getMot(j).getPOS().startsWith("PRO") && !group.getMot(j).getPOS().startsWith("ADJ"))
                                            inWiki =(WikipediaAPI.processPage(group.getMot(j).getForme()).equals(CNConstants.CHAR_NULL))?"F":"T";
                                        outFile.append(lab+"\t"+group.getMot(j).getForme()+"\t"+group.getMot(j).getPOS()+"\t"+ inWiki +"\n");
                                    } 
                                     */                                  
                                    Word word = new Word(j,group.getMot(j).getForme());
                                    word.setPOSTag(group.getMot(j).getPOS(), group.getMot(j).getLemme());
                                    word.setLabel(lab);
                                    word.setUtterance(utt);
                                    words.add(word);
                                    
                                    if(!dictFeatures.containsKey(word.getContent()))
                                        dictFeatures.put(word.getContent(), dictFeatures.size()+1);
                                    
                                    if(!dictFeatures.containsKey(word.getPosTag().getName()))
                                        dictFeatures.put(word.getPosTag().getName(), dictFeatures.size()+1);
                                    
                                    if(!dictFeatures.containsKey(word.getLexicalUnit().getPattern()))
                                        dictFeatures.put(word.getLexicalUnit().getPattern(), dictFeatures.size()+1);         
                            }
                            uttCounter++;
                            utt.setWords(words);
                            
                            
                            //extracting the dependency tree from jsafran to our own format
                            for(int d=0;d<group.deps.size();d++){
                                Dep dep = group.deps.get(d);
                                int headidx=dep.head.getIndexInUtt()-1;
                                Word       head = words.get(headidx);
                                int depidx=dep.gov.getIndexInUtt()-1;
                                Word       governor = words.get(depidx);
                                //Assign the dependencies
                                Dependency dependency;
                                if(!utt.getDepTree().containsHead(head)){
                                    dependency = new Dependency(head);
                                }else{
                                    dependency = utt.getDepTree().getDependency(head);
                                }
                                dependency.addDependent(governor, dep.toString());
                                utt.addDependency(dependency);                            
                            }
                            //sets the roots
                            utt.getDepTree().getDTRoot();
                            utts.add(utt);
                            //add the gold spans
                            for(Pair pair:entitySpans.keySet()){
                                int start = ((Integer) pair.first).intValue();
                                int end = ((Integer) pair.second).intValue();
                                Segment span = new Segment(words.subList(start, end+1));
                                utt.addEntitySpan(entitySpans.get(pair), span);
                                
                            }
                    }
                    for(Utterance utt:utts){
                            /** 
                             * built the tree and vector features here
                             */
                            System.out.println("processing utterance:"+utt);
                            //find all possible spans according to dependency trees                        
                            
                            List<Word> heads = utt.getDepTree().getHeadsInSegment(utt.getSegment().getStart(), utt.getSegment().getEnd());
                            
                            int numbHeadsegsNotEnt=0;
                            for(Word head:heads){
                                HashMap<Segment,String> headSegs = utt.getDepTree().getHeadDepSpans(head,utt.getSegment());
                                
                                int numbSpansNotEnt=0;
                                for(Segment headSegment:headSegs.keySet()){ 
                                    headSegment.computingWordFrequencies();
                                    int label=-1;
                                    if(utt.isEntitySpan(headSegment))
                                        label=utt.getSegment().getWord(headSegment.getEnd()).getLabel();
                                    else
                                        numbSpansNotEnt++;
                                        
                                    //compute frequencies per span

                                    //Feature extraction
                                    String vector="";
                                    String bow="";
                                    HashMap<Integer,Integer> vals= new HashMap<>();
                                    for(Word w:headSegment.getWords()){
                                        bow+=w.getContent()+" ";
                                    }
                                        
                                    for(String pos:headSegment.getPOSFrequencies().keySet()){
                                        int posid=dictFeatures.get(pos);
                                        vals.put(posid,headSegment.getPOSFrequency(pos)); 
                                    } 
                                    
                                    for(String ws:headSegment.getWordShapeFrequency().keySet()){
                                        int wsid= dictFeatures.get(ws);
                                        vals.put(wsid,headSegment.getWordShapeFrequency(ws));
                                    }
                                    
                                    List<Integer> keys= new ArrayList(vals.keySet());
                                    Collections.sort(keys);
                                    for(Integer key:keys){
                                        vector+= key+":"+vals.get(key)+" ";
                                    }
                                    if(onlyVector)
                                        outFile.append(label+"\t"+vector.trim()+"\n");
                                    else{
                                        String tree= utt.getDepTree().getTreeTopDownFeatureForHead(head);
                                        tree=(tree.contains("("))?"|BT| "+ tree+ " |ET|":"|BT| |ET|";
                                        //String treeBUp=utt.getDepTree().getTreeBottomUpFeatureForHead(word,"");
                                        //treeBUp=(treeBUp.contains("("))?" |BT| ("+ treeBUp + ") |ET|":"";
                                        tree=tree.trim(); //+ treeBUp.trim();    
                                        //outFile.append(label+" "+headSegment +"\t"+tree.trim()+" "+vector+"\n");
                                        //outFile.append(label+"\t"+bow.trim()+" "+tree.trim()+" "+vector.trim()+"\n");
                                        outFile.append(label+"\t"+" "+tree.trim()+" "+vector.trim()+"\n");
                                    }   
                                    totalOfspans++;
                                    
                                }  
                                if(numbHeadsegsNotEnt==headSegs.size()&& !utt.getGoldEntities().isEmpty()){
                                    ErrorsReporting.report("not entity span covers a head segment in head: + "+ head.getContent());
                                    numbHeadsegsNotEnt++;
                                }    
                            }
                            //all leaves
                            List<Word> leaves = utt.getDepTree().getLeaves();
                            for(Word leaf:leaves){
                                //Segment leafSeg=utt.getSegment().subSegment(leaf.getPosition(), leaf.getPosition());
                                                               
                                //if(istrain && !utt.isEntitySpan(leafSeg))
                                //    continue;
                                    
                                int posid=dictFeatures.get(leaf.getPosTag().getName());
                                int wsid= dictFeatures.get(leaf.getLexicalUnit().getPattern());
                                String vector=posid+":1 "+wsid+":1";
                                if(posid>wsid)
                                    vector=wsid+":1 "+posid+":1";
                                                                   
                                String tree="|BT| |ET|";
                                //outFile.append(leaf.getLabel()+"\t"+ leaf.getContent()+" "+tree.trim()+" "+vector.trim()+"\n");
                                outFile.append(leaf.getLabel()+"\t"+ tree.trim()+" "+vector.trim()+"\n");
                                    
                            }
                            
                            if(numbHeadsegsNotEnt==heads.size() && !utt.getGoldEntities().isEmpty()){
                                ErrorsReporting.report("not entity span covers a head segment in utt: + "+ utt.getId());
                            }
                    }
                     if(istrain && uttCounter> TRAINSIZE){
                        break;
                    }                 
                }
                outFile.flush();
                outFile.close();
                inFile.close();
                ErrorsReporting.report("groups saved in groups.*.tab || utterances:"+uttCounter+" spans: "+totalOfspans);
            } catch (IOException e) {
                    e.printStackTrace();
            }
    }   
    
     public void parsing(boolean bltrain){
            try {
                GraphIO gio = new GraphIO(null);
                OutputStreamWriter outFile =null;
                String xmllist=LISTTRAINFILES;
                if(!bltrain)
                     xmllist=LISTTESTFILES;
                    
                
                BufferedReader inFile = new BufferedReader(new FileReader(xmllist));
//                OutputStreamWriter bigconllFile = new OutputStreamWriter(new FileOutputStream(new File("parse/all.in.conll"),true), CNConstants.UTF8_ENCODING);
                for (;;) {
                    String s = inFile.readLine();
                    if (s==null) break;
                    List<DetGraph> graphs = gio.loadAllGraphs(s);
                    String filename=s.trim().replaceAll("[\\s]+"," ");
//                    String inconll="parse/"+filename+".in.conll";
                    File outfile=new File("parse/"+filename+".out.conll");
                    System.out.println("Processing file: "+ filename);
                    if(outfile.exists())
                        continue;
                    
//                    GraphIO.saveConLL09(graphs, null, inconll);
//                    BufferedReader inconllFile= new BufferedReader(new FileReader(inconll));
//                    
//                    for(;;){
//                        String line=inconllFile.readLine();
//                        if (line==null) break;
//                        bigconllFile.append(line);
//                    }
//                    bigconllFile.flush();  
                    POStagger.setFrenchModels();
                    MateParser.setMods("mate.mods.ETB"); 
                    try{
                        MateParser.parseAll(graphs);
                    }catch(Exception ex){
                        System.out.println("ERROR PARSING FILE : "+filename);
                        continue;
                    }    
                }      
            } catch (IOException e) {
                    e.printStackTrace();
            }
                    
     }
 
    public void evaluationSVMLightRESULTS(String testFileName, String outputFileName){
        BufferedReader testFile=null, svmOutput = null;
        try {
            //"analysis/SVM/groups.pn.tab.treek.test"
            //"analysis/SVM/outmodel200000testset.txt"
            
            testFile = new BufferedReader(new InputStreamReader(new FileInputStream(testFileName), UTF8_ENCODING));
            svmOutput = new BufferedReader(new InputStreamReader(new FileInputStream(outputFileName), UTF8_ENCODING));
            int tp=0, tn=0, fp=0, fn=0;
            for(;;){

                String line = testFile.readLine();   
                String result = svmOutput.readLine();
                
                if(line== null)
                    break;                
                
                String values[] = line.split("\\t");
                String res[] = result.split("\\t");
                int label = Integer.parseInt(values[0]);
                float recognizedLabel = Float.parseFloat(res[0]);
                int ok=1, nok=-1;
                
                if(recognizedLabel>0 && label==ok)
                    tp++;
                
                if(recognizedLabel>0 && label==nok)
                    fp++;
                
                if(recognizedLabel<0 &&label==ok)
                    fn++;
                if(recognizedLabel<0 &&label==nok)
                    tn++;

            }
            double precision= (double) tp/(tp+fp);
            double recall= (double) tp/(tp+fn);
            double f1=(2*precision*recall)/(precision+recall);
            
            System.out.println("  PN precision: "+precision);
            System.out.println("  PN recall: "+recall);
            System.out.println("  PN f1: "+f1);
            
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            try {
                testFile.close();
                svmOutput.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
       
       
   } 
 
    public void savingAllSpansFiles(String strclass, boolean isvector){
        saveFilesForClassifierAllSpans(strclass, true, isvector);
        saveFilesForClassifierAllSpans(strclass, false, isvector);
    }    
    
    public void savingSpansFiles(String strclass, boolean isvector){
        saveFilesForLClassifierSpans(strclass, isvector);
        saveFilesForClassifierAllSpans(strclass, false, isvector);
    }
     
    public void savingWordsFiles(String strclass, boolean isvector){
        saveFilesForLClassifierWords(strclass,true, isvector);
        saveFilesForLClassifierWords(strclass,false, isvector);
    }    
     public static void main(String args[]){
         AnalyzeSVMClassifier svmclass= new AnalyzeSVMClassifier();
         //svmclass.parsing(true);
         //svmclass.saveFilesForLClassifierWords(CNConstants.PRNOUN, true,false);
         //testset
         //svmclass.saveFilesForLClassifierWords(CNConstants.PRNOUN, false,false);
         //spans
         //train & test
         //entity class / vector feature
        // svmclass.savingWordsFiles(CNConstants.PRNOUN, false);
         //svmclass.savingWordsFiles(CNConstants.PRNOUN, false);
         //svmclass.evaluationSVMLightRESULTS("analysis/SVM/groups.pn.tab.sptk.test", "analysis/SVM/output_spans_jul82014.txt");
         //svmclass.savingSpansFiles(CNConstants.PRNOUN, false);
         svmclass.savingAllSpansFiles(CNConstants.PRNOUN, false);
     }
      
}
