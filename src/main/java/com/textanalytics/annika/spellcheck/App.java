package com.textanalytics.annika.spellcheck;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngine;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import de.tudarmstadt.ukp.dkpro.core.api.anomaly.type.SpellingAnomaly;
import de.tudarmstadt.ukp.dkpro.core.jazzy.JazzyChecker;
import de.tudarmstadt.ukp.dkpro.core.opennlp.OpenNlpSegmenter;

public class App {

	//dictionaries
	static String dictJazzy = "jazzy_orig.txt";
	static String dictHunspell = "hunspell_en_US.txt";
	
	//file with prompt (P4), language and essayId number
	static String csvFile = "src/main/resources/labels.train.csv";
	
	static String german = "GER";
	static String chinese = "CHI";

	public static void main(String[] args) {

		//select essayIds, where promp=4 and language=german or = chinese
		ArrayList<String> essayIdsGER = readCSV(csvFile, german);
		ArrayList<String> essayIdsCHI = readCSV(csvFile, chinese);
		
		//pass through every essay and writes the spelling mistakes in one file (one file for german, one for chinese and one file for every dictionary
		// generateOutput(essayIdsGER, german);
		// generateOutput(essayIdsCHI, chinese);
		
		//for evaluation	count words for every essay and in total for every language
		//ArrayList<Pair<String, Integer>> wordsOfEssaysGER = countWords(essayIdsGER);
		//ArrayList<Pair<String, Integer>> wordsOfEssaysCHI = countWords(essayIdsCHI);
		
		//int totalCounterGer = totalWordCounter(wordsOfEssaysGER);
		//int totalCounterChi = totalWordCounter(wordsOfEssaysCHI);
		
		/*System.out.println(wordsOfEssaysGER);
		System.out.println(wordsOfEssaysGER.size());
		System.out.println(totalCounterGer);
		
		System.out.println(wordsOfEssaysCHI);
		System.out.println(wordsOfEssaysCHI.size());
		System.out.println(totalCounterChi);*/
		
		//zählt anzahl von knowledge
		
		System.out.println(essayIdsGER);
		int knowledgeC = countKnowledge(essayIdsCHI);
		int knowledgeG = countKnowledge(essayIdsGER);
		System.out.println("number of knowledge in Chinese: " + knowledgeC);
		System.out.println("number of knowledge in German: " + knowledgeG);
		
		int specialC = countSpecial(essayIdsCHI);
		int specialG = countSpecial(essayIdsGER);
		System.out.println("number of forms of special in Chinese: " + specialC);
		System.out.println("number of forms of special in German: " + specialG);
	}

	public static ArrayList<String> readCSV(String csvFile, String language) {
		BufferedReader br = null;
		ArrayList<String> ids = new ArrayList<String>();
		try {
			br = new BufferedReader(new FileReader(csvFile));	//labels-file 
			String zeile = "";

			while ((zeile = br.readLine()) != null) { // read every line
				String[] split = zeile.split(","); // split = {id, promptspeak, promptEssay, L1} --> promptspeak not relevant
				if (split[2].equals("P4") && split[3].equals(language)) {	//prompt should be 4 and language german or chinese
					ids.add(split[0]);					// id from essay
				}
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return ids;
	}
	
	public static void generateOutput(ArrayList<String> essayIds, String language) {
		for (int i = 0; i < essayIds.size(); i++) {
			detectMistakes(dictHunspell, essayIds.get(i), language);		//for every essay search for spelling mistakes
			detectMistakes(dictJazzy, essayIds.get(i), language);
		}
	}

	public static void detectMistakes(String dict, String id, String language) {
		String txtFile = "src/main/resources/data/" + id + ".txt";	//look up the textfile with the essay

		BufferedReader br = null;
		BufferedWriter bw = null;

		String zeile = "";

		String output = language + "output" + dict; // output-Datei name

		try {

			br = new BufferedReader(new FileReader(txtFile));
			bw = new BufferedWriter(new FileWriter(output, true));	//all spelling mistakes from one language in one file

			while ((zeile = br.readLine()) != null) { // reads every line

				AnalysisEngineDescription segmenter = createEngineDescription(OpenNlpSegmenter.class,
						OpenNlpSegmenter.PARAM_LANGUAGE, "en"); // Tokenizer and sentence splitter

				AnalysisEngineDescription spellChecker = createEngineDescription(JazzyChecker.class,
						JazzyChecker.PARAM_MODEL_LOCATION, "src/main/resources/" + dict); // decision whether a word is
																							// spelled correctly or not by the dictionary file

				AnalysisEngineDescription description = createEngineDescription(segmenter, spellChecker);
				AnalysisEngine engine = createEngine(description);

				JCas jcas = engine.newJCas();
				jcas.setDocumentLanguage("en");
				jcas.setDocumentText(zeile);
				engine.process(jcas);
				
				Pattern regexPatternZahl = Pattern.compile("[0-9]+");		//regular expression for numbers
				Pattern regexPatternSonderzeichen = Pattern.compile("(\\w*'\\w*)|''|``");	//regular expression for special characters
				
				for (SpellingAnomaly errorAnnotation : JCasUtil.select(jcas, SpellingAnomaly.class)) { // Fehler finden
					String mistake = errorAnnotation.getCoveredText();	//finds mistake
					
					Matcher m1 = regexPatternZahl.matcher(mistake);
					Matcher m2 = regexPatternSonderzeichen.matcher(mistake);
					
					if (!m1.matches() && !m2.matches() && !mistake.equals("a") && !mistake.equals("i")
							&& !mistake.equals("A") && !mistake.equals("I")) {	//no mistake if the string is a number or special character or a and i
						bw.write(mistake); // Fehler in Dokument schreiben
						bw.newLine();
					}
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (ResourceInitializationException e) {
			e.printStackTrace();
		} catch (AnalysisEngineProcessException e) {
			e.printStackTrace();
		} finally {
			if (br != null) {
				try {
					br.close();
					bw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

	}
	
	public static ArrayList<Pair<String, Integer>> countWords(ArrayList<String> essayIds) {
		ArrayList<Pair<String, Integer>> wordsOfEssayId = new ArrayList<Pair<String, Integer>>();	//pair of essayId and number of words
		int words = 0;
		Pattern regexPatternKeinBuchstabe = Pattern.compile("\\W*");
		for (int j = 0; j < essayIds.size(); j++) {	//count words for every essay
			String id = essayIds.get(j);
			String txtFile = "src/main/resources/data/" + id + ".txt";

			BufferedReader br = null;
			String zeile = "";

			try {

				br = new BufferedReader(new FileReader(txtFile));

				while ((zeile = br.readLine()) != null) { // reads every line
					String[] split = zeile.split(" "); // split = {words of the line}
					for (int i = 0; i < split.length; i++) {
						Matcher m = regexPatternKeinBuchstabe.matcher(split[i]);
						if (!m.matches()) {
							words = words + 1;
						}
					}
				}
				Pair<String, Integer> pair = Pair.of(id, words);
				wordsOfEssayId.add(pair);	//add pair of essayId and words to arraylist
				words = 0;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return wordsOfEssayId;
	}
	
	public static int totalWordCounter(ArrayList<Pair<String, Integer>> wordsOfEssays) {
		int counter = 0; 
		for(int i=0; i<wordsOfEssays.size(); i++ ) {
			int value = wordsOfEssays.get(i).getValue();
			counter = counter + value;		//count all words from one languageT
		}
		return counter;
	}


	public static int countKnowledge(ArrayList<String> essayIds) {
		int words = 0;
		Pattern regexPatternKnowledge = Pattern.compile("knowledge");
		for (int j = 0; j < essayIds.size(); j++) { // count knowledge words for every essay
			String id = essayIds.get(j);
			String txtFile = "src/main/resources/data/" + id + ".txt";

			BufferedReader br = null;
			String zeile = "";

			try {

				br = new BufferedReader(new FileReader(txtFile));

				while ((zeile = br.readLine()) != null) { // reads every line
					String[] split = zeile.split(" "); // split = {words of the line}
					for (int i = 0; i < split.length; i++) {
						Matcher mat = regexPatternKnowledge.matcher(split[i]);
						if (mat.matches()) {
							words = words + 1;
						}
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return words;
	}
	
	public static int countSpecial(ArrayList<String> essayIds) {
		int words = 0;
		Pattern regexPatternKnowledge = Pattern.compile("special|specialization|specialisation|specific|specialize|specialise|specialist|specifically|specify|specialty|speciality");
		for (int j = 0; j < essayIds.size(); j++) { // count knowledge words for every essay
			String id = essayIds.get(j);
			String txtFile = "src/main/resources/data/" + id + ".txt";

			BufferedReader br = null;
			String zeile = "";

			try {

				br = new BufferedReader(new FileReader(txtFile));

				while ((zeile = br.readLine()) != null) { // reads every line
					String[] split = zeile.split(" "); // split = {words of the line}
					for (int i = 0; i < split.length; i++) {
						Matcher mat = regexPatternKnowledge.matcher(split[i]);
						if (mat.matches()) {
							words = words + 1;
						}
					}
				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (br != null) {
					try {
						br.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return words;
	}
}

