import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;

public class Sudi {
    public HashMap<String, Integer> occurrence; // map of tags --> their frequency
    public HashMap<String, HashMap<String, Double>> observations; // map of words --> (map of tags --> number of times a word is associated with a tag)
    public HashMap<String, HashMap<String, Double>> transitions; // map of tags --> (map to transitioned tag --> number of times that tag pair transition occurs)
    public ArrayList<ArrayList<String>> sentences; // List of list containing sentence tags

    /**
     * parses the training data to build score observation and transition graphs
     *
     * @throws IOException if the file is empty
     */
    public Sudi() throws IOException {

        // initiate the relevant maps
        occurrence = new HashMap<>(); observations = new HashMap<>(); transitions = new HashMap<>();

        // put in the # for start for both occurrence and transitions
        occurrence.put("#", 0); transitions.put("#", new HashMap<>());

        // read into the train word file and the train tag file
        BufferedReader in = new BufferedReader(new FileReader("texts/brown-train-tags.txt"));
        BufferedReader in2 = new BufferedReader(new FileReader("texts/brown-train-sentences.txt"));

        String line; // for tag file
        String line2; // for word file

        // uses both of lines for condition to be able to read into both simultaneously
        while((line = in.readLine())!= null & (line2 = in2.readLine())!= null){

            // splits lines at all white spaces
            String[] tags = line.split("\\s");
            String[] words = line2.split("\\s");

            // indicates that this is the start of a new sentence
            occurrence.put("#", occurrence.get("#")+1);

            // indicates that this tag start a new sentence and maps the start indicator to the tag
            if(!transitions.get("#").containsKey(tags[0])) {
                transitions.get("#").put(tags[0], (double) 1);
            }
            else {
                transitions.get("#").put(tags[0], transitions.get("#").get(tags[0])+1);
            }

            // checks if both lines are of equal length; unequal lines would cause mapping errors
            if (tags.length == words.length) {

                for (int i = 0; i < tags.length; i++){

                    String x1 = tags[i].toLowerCase(); // gets the tag at the position i in the tag array
                    String x2 = words[i].toLowerCase(); // gets the word at the position i in the word array

                    // tag --> to --> number of times the tag occurs
                    if(!occurrence.containsKey(x1)){

                        occurrence.put(x1, 1);
                    }
                    else{
                        occurrence.put(x1, occurrence.get(x1)+1);
                    }

                    // word observation --> to --> POS and number of times per POS for word
                    if(!observations.containsKey(x2)){
                        observations.put(x2, new HashMap<>());
                        observations.get(x2).put(x1, (double) 1);
                    }
                    else {
                        if(!observations.get(x2).containsKey(x1)){
                            observations.get(x2).put(x1, (double) 1);
                        }
                        else {
                            observations.get(x2).put(x1, observations.get(x2).get(x1)+1);
                        }
                    }

                    // tag --> to --> next tag it transitions to and number of transition to particular next tag
                    if(i<tags.length - 1){
                        if(!transitions.containsKey(x1)){
                            transitions.put(x1, new HashMap<>());
                            transitions.get(x1).put(tags[i+1], (double) 1);
                        }
                        else {
                            if(!transitions.get(x1).containsKey(tags[i+1])){
                                transitions.get(x1).put(tags[i+1], (double) 1);
                            }
                            else{
                                transitions.get(x1).put(tags[i+1], transitions.get(x1).get(tags[i+1])+1);
                            }
                        }
                    }
                }
            }
        }

        // normalizing and converting transition values  to log
        for (String tag: occurrence.keySet()){
            if (transitions.containsKey(tag)) {
                for(String tag0:transitions.get(tag).keySet()) {
                    transitions.get(tag).put(tag0, Math.log10(transitions.get(tag).get(tag0) / occurrence.get(tag)));
                }
            }
        }

        // normalizing and converting observation values  to log
        for(String word: observations.keySet()){
            for(String tag:observations.get(word).keySet()){
                double x0 = observations.get(word).get(tag)/occurrence.get(tag);
                observations.get(word).put(tag, Math.log10(x0));
            }
        }

        requestCommand();
    }

    /**
     * evaluates a current state for an observation and returns a map of next states and next scores
     *
     * @param observation word being observed
     * @param cState current tag state
     * @param cScore current state score
     * @return Map of tag --> score as next states and score
     */
    public HashMap<String, Double> evaluate(String observation, String cState, double cScore){
        HashMap<String, Double> nextStates = new HashMap<>(); // will contain the nextStates and the scores from the evaluation
        double u = -100.0; // observation score for when a value in undiscovered

        if(transitions.containsKey(cState.toLowerCase())) {
            for (String nState : transitions.get(cState.toLowerCase()).keySet()) {
                double oScore;

                if (observations.containsKey(observation) && observations.get(observation.toLowerCase()).containsKey(nState.toLowerCase())) {
                    oScore = observations.get(observation.toLowerCase()).get(nState.toLowerCase()); //the observation score
                }
                else {
                    oScore = u; // undiscovered observation score
                }
                double tScore = transitions.get(cState.toLowerCase()).get(nState); //the transition score
                nextStates.put(nState, cScore + tScore + oScore); // adds the next state and its score to the map
            }
        }

        return nextStates; // returns the map of next states
    }

    /**
     * gets a text file and tags it
     *
     * @param sFile test sentence file
     * @throws IOException if file is empty
     */
    public void tagFile(String sFile) throws IOException {

        BufferedReader in = new BufferedReader(new FileReader(sFile)); // reads words from text file

        String line;

        sentences = new ArrayList<>(); // will hold a list of list of tags

        while((line = in.readLine())!= null){ // read through the file by line

            String[] words = line.split("\\s"); // split the line at white spaces
            ArrayList<String> tagList = evalSentence(words);
            sentences.add(tagList); // adds the list to the list of sentences; each list serves as the tags for any given sentence
        }
        requestCommand();
    }

    /**
     * assigns tags to a sentence using the training data
     *
     * @param splitArray arrays of sentence words
     * @return list of sentence tags
     */
    public ArrayList<String> evalSentence(String[] splitArray) {

        ArrayList<HashMap<String, Double>> stages = new ArrayList<>(); // tracks all evaluations at a given stage/observation

        ArrayList<HashMap<String, String>> stageTrack = new ArrayList<>(); // tracks the path used to reach a given state

        int stage = 1; // current stage of a line i.e current word of the line being "observed"

        HashMap<String, Double> nextStates = evaluate(splitArray[0], "#", 0); // evaluates the start of the sentence
        stages.add(nextStates); // adds the evaluation as stages

        for(int i = 1; i<splitArray.length; i++) {

            HashMap<String, Double> cStates = stages.get(stage-1); // map of the now current states from the previous stage
            HashMap<String, Double> newStates = new HashMap<>(); // map of the new next states
            HashMap<String, String> tracks = new HashMap<>(); // paths used to reach the new next states

            for (String state:cStates.keySet()){ // goes through the set of current states
                HashMap<String, Double> tStates = evaluate(splitArray[i], state, cStates.get(state)); // evaluates the current state
                for(String nState:tStates.keySet()){ // goes through the discovered next states
                    if (!newStates.containsKey(nState)){ // adds a next state and its score to newStates if discovered for the first time
                        newStates.put(nState, tStates.get(nState));
                    }
                    if(!tracks.containsKey(nState)){ // adds next state and its predecessor if discovered for the first time
                        tracks.put(nState, state);
                    }
                    if(tStates.get(nState) > newStates.get(nState)){ // updates the score and predecessor of a next state if a better score is found from a different path
                        newStates.put(nState, tStates.get(nState));
                        tracks.put(nState, state);
                    }
                }
            }
            stages.add(newStates); // add the map of next states to stages
            stageTrack.add(tracks); // adds the tracks from observation to stageTrack
            stage++; // updates to evaluate the next stage i.e the stage just added
        }

        HashMap<String, Double> end = stages.get(stages.size()-1); // gets the last stage i.e state map of last word observed
        String tag = null; Double score = null; // tag holds the current best score tag for a stage/word in a line
        for (String tagX: end.keySet()){
            // finds the tag with the best score under an observation stage; only does this for the last stage
            if(score == null){
                score = end.get(tagX);
                tag = tagX;
            }
            if(score < end.get(tagX)){
                score = end.get(tagX);
                tag = tagX;
            }
        }

        ArrayList<String> tagList = new ArrayList<>(); // list of strings to holds the sentence tags
        tagList.add(0, tag); // adds the last tag gotten above to the list
        for(int count = stageTrack.size()-1; count >= 0; count--){
            tag = stageTrack.get(count).get(tag); // updates tag to its predecessor
            tagList.add(0, tag); // adds the updated tag to the tag list
        }
        System.out.println(tagList);
        return tagList;
    }

    /**
     * gets and tags a sentence from the user
     *
     * @param sentence user input
     * @throws IOException empty line
     */
    public void fromConsole(String sentence) throws IOException {
        String[] words = sentence.split("\\s");
        ArrayList<String> tags = evalSentence(words);
        StringBuilder taggedSentence = new StringBuilder();
        for(int i =0; i < words.length; i++){
            taggedSentence.append(words[i]).append("/").append(tags.get(i)).append(" ");
        }
        System.out.println(taggedSentence);
        requestCommand();
    }


    /**
     *Calculate and prints out file tagging accuracy
     *
     * @param tFile test tag file
     * @throws IOException if file is empty
     */
    public void tagAccuracy(String tFile) throws IOException {
        BufferedReader in = new BufferedReader(new FileReader(tFile)); // reads in from the test verification tag file

        String line;
        int sentence = 0; // keeps track of the sentence bring considered
        double tTags = 0; double tAccurate = 0; double tWrong = 0; // total number of tag pairs; number of correct pairs; number of wrong pairs

        while((line = in.readLine())!= null){
            ArrayList<String> tagList = sentences.get(sentence); // gets the relevant sentence from the list of sentence tag lists i.e sentences
            String[] tags = line.split("\\s"); // splits line at the white spaces

            for (int i = 0; i < tags.length; i++){
                if(tagList.get(i).compareTo(tags[i]) == 0){ // checks if the tags correspond
                    tAccurate++; // increments the count for number of correct pairs
                }
                else {
                    tWrong += 1; // increments the count for number of wrong pairs
                }
                tTags++; // increments the count for total number of pairs
            }
            sentence++; // updates to consider next sentence
        }
        // prints number of correct pairs and wrong pair
        System.out.println("Out of " + tTags + ", " + tAccurate +" tags correct and " + tWrong + "tags wrong");
        // prints the percentage accuracy
        System.out.println("Percentage accuracy of: "+tAccurate/tTags*100+"%");
        requestCommand();
    }

    /**
     * requests and processes input from the console
     *
     * @throws IOException file is empty
     */
    public void requestCommand() throws IOException {
        System.out.println("f >> Tag a file \ni >> Use your own sentence \na >> Tagging accuracy * Files Only \nt >> PartS Of Speech Guide \nq >> Quit program \nEnter command >>");
        Scanner in = new Scanner(System.in);
        String key = in.nextLine();
        handleKeyPress(key);
    }

    /**
     * handles key commands from the console
     *
     * @param key command input
     * @throws IOException file is empty
     */
    public void handleKeyPress(String key) throws IOException {
        String[] input = key.split(" ");

        if (key.equals("f")) {
            System.out.println("Enter file names to tag");
            Scanner in = new Scanner(System.in);
            String name = in.nextLine();
            tagFile(name);
        }
        else if (key.equals("i")) {
            System.out.println("Enter sentence >> ");
            Scanner in = new Scanner(System.in);
            String sentence = in.nextLine();
            fromConsole(sentence);
        }
        else if (key.equals("a")) {
            System.out.println("Tag verifier file name >>");
            Scanner in = new Scanner(System.in);
            String name = in.nextLine();
            tagAccuracy(name);
        }
        else if (key.equals("t")) {
            System.out.println("ADJ\tadjective\tnew, good, high, special, big, local\n" +
                    "ADV\tadverb\treally, already, still, early, now\n" +
                    "CNJ\tconjunction\tand, or, but, if, while, although\n" +
                    "DET\tdeterminer\tthe, a, some, most, every, no\n" +
                    "EX\texistential\tthere, there's\n" +
                    "FW\tforeign word\tdolce, ersatz, esprit, quo, maitre\n" +
                    "MOD\tmodal verb\twill, can, would, may, must, should\n" +
                    "N\tnoun\tyear, home, costs, time, education\n" +
                    "NP\tproper noun\tAlison, Africa, April, Washington\n" +
                    "NUM\tnumber\ttwenty-four, fourth, 1991, 14:24\n" +
                    "PRO\tpronoun\the, their, her, its, my, I, us\n" +
                    "P\tpreposition\ton, of, at, with, by, into, under\n" +
                    "TO\tthe word to\tto\n" +
                    "UH\tinterjection\tah, bang, ha, whee, hmpf, oops\n" +
                    "V\tverb\tis, has, get, do, make, see, run\n" +
                    "VD\tpast tense\tsaid, took, told, made, asked\n" +
                    "VG\tpresent participle\tmaking, going, playing, working\n" +
                    "VN\tpast participle\tgiven, taken, begun, sung\n" +
                    "WH\twh determiner\twho, which, when, what, where, how\n");
            requestCommand();
        }
        else if (key.equals("q")) {
            System.out.println("Exited Game!");
            System.exit(0);
        }
        else {
            System.out.println("Invalid command");
            requestCommand();
        }
        key = null;
    }


    public static void main(String[] args) throws IOException {
        Sudi xx = new Sudi();
    }
}



