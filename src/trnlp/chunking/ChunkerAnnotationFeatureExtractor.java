package trnlp.chunking;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jcaki.SimpleTextReader;
import org.jcaki.Strings;
import trnlp.apps.ContentPreprocessor;
import trnlp.apps.CrfTemplates;
import trnlp.apps.TurkishMorphology;
import trnlp.apps.TurkishSentenceTokenizer;
import zemberek3.parser.morphology.MorphParse;
import zemberek3.parser.morphology.SentenceMorphParse;
import zemberek3.shared.lexicon.SecondaryPos;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * This class is used for extracting feature data and files necessary for generating CRF models.
 */
public class ChunkerAnnotationFeatureExtractor {

    ContentPreprocessor preprocessor = new ContentPreprocessor();
    static TurkishSentenceTokenizer tokenizer = new TurkishSentenceTokenizer();
    TurkishMorphology morphology;
    boolean eliminatePunctuations = false;

    String delimiter = " ";

    public ChunkerAnnotationFeatureExtractor() throws IOException {
        this.morphology = new TurkishMorphology();
    }

    public ChunkerAnnotationFeatureExtractor(String delimiter, boolean eliminatePunctuations) throws IOException {
        this.morphology = new TurkishMorphology();
        this.delimiter = delimiter;
        this.eliminatePunctuations = eliminatePunctuations;
    }

    public void generateFromAnnotationFile(File in, File out) throws IOException {
        PrintWriter pw = new PrintWriter(out, "utf-8");

        // load all sentences
        List<String> lines = SimpleTextReader.trimmingUTF8Reader(in).asStringList();

        // eliminate duplicated sentences and give statistics
        Set<List<ChunkData>> accepted = new LinkedHashSet<>();
        Set<String> declined = new LinkedHashSet<>();
        Set<String> ignoredChunkSentences = new LinkedHashSet<>();
        for (String line : lines) {
            line = line.replaceAll("[*]", "");

            if (accepted.contains(line)) {
                System.out.println("Duplicated line: " + line);
            } else {
                List<ChunkData> chunks = new ArrayList<>();
                // split from chunks
                boolean chunkIgnored = false;
                for (String s : Splitter.on("/").trimResults().omitEmptyStrings().split(line)) {
                    ChunkData chunkData = ChunkData.generate(s, eliminatePunctuations);
                    if (chunkData == null) {
                        System.out.println("Bad chunk detected:[" + s + "]  in sentence:" + line);
                        chunkIgnored = true;
                    } else
                        chunks.add(chunkData);
                }
                if (chunks.isEmpty()) {
                    declined.add(line);
                } else {
                    accepted.add(chunks);
                    if (chunkIgnored)
                        ignoredChunkSentences.add(line);
                }
            }
        }


        System.out.println("Accepted with ignored chunks");
        for (String ignoredChunkSentence : ignoredChunkSentences) {
            System.out.println(ignoredChunkSentence);
        }
        System.out.println();
        System.out.println("Declined sentences: ");
        for (String s : declined) {
            System.out.println(s);
        }

        System.out.println("Total Sentence count:" + lines.size());
        System.out.println("Accepted Line count:" + accepted.size());
        System.out.println("Accepted with loss of chunk count:" + ignoredChunkSentences.size());
        System.out.println("Declined Line count:" + declined.size());

        for (List<ChunkData> chunkDataList : accepted) {

            List<WordFeature> wordFeatures = new ArrayList<>();
            List<String> chunks = new ArrayList<>();

            for (ChunkData chunkData : chunkDataList) {
                String processed = preprocessor.process(chunkData.wordsBlock);
                List<String> words = tokenizer.tokenizeAsStrings(processed);
                for (int i = 0; i < words.size(); i++) {
                    String word = words.get(i);
                    String label;
                    if (i == 0)
                        label = chunkData.tag.label + "B";
                    else
                        label = chunkData.tag.label + "I";
                    wordFeatures.add(new WordFeature(word, label));
                }
                chunks.addAll(words);
            }
            String allSentence = Joiner.on(" ").join(chunks);

            List<String> featureLines = getFeatureLines(wordFeatures, allSentence);

            if (featureLines == null) {
                System.out.println("Problem extracting features: " + allSentence);
                continue;
            }
            for (String fl : featureLines) {
                // stw.writeLine(labeledWord.word + " " + labeledWord.label);
                pw.print(fl);
                pw.print('\n');
            }
            pw.print('\n');

        }
        pw.close();
    }

    public List<String> getFeatureLines(List<WordFeature> wordFeatures, String allSentence) {
        SentenceMorphParse parse = null;
        try {
            parse = morphology.parseAndDisambiguateSentence(allSentence);
        } catch (Exception e) {
            System.out.println("Error during morphological parse of sentence:" + allSentence + " with exception:" + e.getMessage());
        }

        if (parse == null) {
            return null;
        }

        for (int i = 0; i < wordFeatures.size(); i++) {
            WordFeature wordFeature = wordFeatures.get(i);
            try {
                wordFeature.features = new TurkishChunkFeatures(wordFeature.token, parse.getEntry(i).parses.get(0));
            } catch (Exception e) {
                System.out.println("Error during feature extraction of sentence:" + allSentence + " with exception:" + e.getMessage());
            }
        }

        List<String> featureLines = new ArrayList<>();

        for (WordFeature wordFeature : wordFeatures) {
            TurkishChunkFeatures current = wordFeature.features;
            List<String> features = current.getFeatureList();
            String label = wordFeature.label;
            features.add(label);
            featureLines.add(Joiner.on(delimiter).join(features));
        }
        return featureLines;
    }

    static Set<String> puncts = Sets.newHashSet(".", ",", "?", ":", ";", "!");

    public static String eliminatePunctuations(String s) {
        List<String> tokens = tokenizer.tokenizeAsStrings(s);
        List<String> result = new ArrayList<>();
        for (String token : tokens) {
            if (!puncts.contains(token))
                result.add(token);
        }
        return Joiner.on(" ").join(result);
    }


    class WordFeature {
        String token;
        String label;
        TurkishChunkFeatures features;

        @Override
        public String toString() {
            return features.featureString(delimiter, label);
        }

        WordFeature(String token, String label) {
            this.token = token;
            this.label = label;
        }

        WordFeature(String label, TurkishChunkFeatures features) {
            this.label = label;
            this.features = features;
        }
    }

    public static enum ChunkType {
        SUBJECT("S"), OBJECT("O"), ADJUNCT("A"), VERB("V");

        String label;

        static Map<String, ChunkType> typeMap = new HashMap<>();

        static {
            for (ChunkType s : ChunkType.values()) {
                typeMap.put(s.label, s);
            }
        }

        ChunkType(String abbrv) {
            this.label = abbrv;
        }

        static ChunkType getByAbbrv(String abbrv) {
            return typeMap.get(abbrv);
        }

    }

    static Map<String, ChunkType> annotationMap = Maps.newHashMap();

    static {
        annotationMap.put("[Tümleç]", ChunkType.ADJUNCT);
        annotationMap.put("[Özne]", ChunkType.SUBJECT);
        annotationMap.put("[Nesne]", ChunkType.OBJECT);
        annotationMap.put("[Yüklem]", ChunkType.VERB);
    }

    public static class ChunkData {
        String wordsBlock;
        ChunkType tag;

        public ChunkData(String wordsBlock, ChunkType tag) {
            this.wordsBlock = wordsBlock;
            this.tag = tag;
        }

        static ChunkData generate(String chunk, boolean eliminatePunctuations) {
            chunk = chunk.trim();
            String wordsBlock = null;
            ChunkType tag = null;

            for (String annotation : annotationMap.keySet()) {
                if (chunk.endsWith(annotation)) {
                    wordsBlock = Strings.subStringUntilLast(chunk, annotation);
                    if (eliminatePunctuations)
                        wordsBlock = eliminatePunctuations(wordsBlock);
                    tag = annotationMap.get(annotation);
                    break;
                }
            }

            // line is not proper, we return null.
            if (wordsBlock == null || tag == null)
                return null;

            return new ChunkData(wordsBlock, tag);
        }

        @Override
        public String toString() {
            return wordsBlock + " [" + tag.name() + "]";
        }
    }

    public static class TurkishChunkFeatures {
        String word;
        String lemma;
        String pos;
        String secPos;
        String lastIg;
        boolean firstLetterCapital = false;
        boolean containsQuote = false;
        String last3;
        boolean plural;
        String nounCase;

        public static final TurkishChunkFeatures START = new TurkishChunkFeatures("<s>");
        public static final TurkishChunkFeatures END = new TurkishChunkFeatures("<s>");

        TurkishChunkFeatures(String word, MorphParse parse) {
            this.word = word;
            this.lemma = parse.dictionaryItem.lemma;
            this.pos = parse.getPos().shortForm;
            this.secPos = parse.dictionaryItem.secondaryPos.shortForm;
            if (this.secPos.equals("Unk"))
                this.secPos = "_";
            if (parse.dictionaryItem.secondaryPos == SecondaryPos.ProperNoun)
                pos = "Prop";
            if (parse.getSuffixDataList().size() == 0) {
                this.lastIg = "_";
            } else {
                StringBuilder sb = new StringBuilder();
                List<MorphParse.SuffixData> suffixList = parse.getLastIg().suffixList;
                for (int j = 0; j < suffixList.size(); j++) {
                    sb.append(suffixList.get(j).suffix.id);
                    if (j < suffixList.size() - 1)
                        sb.append("+");
                }
                this.lastIg = sb.toString();
                if (this.lastIg.length() == 0)
                    this.lastIg = "_";
            }
            this.firstLetterCapital = Character.isUpperCase(word.charAt(0));
            this.containsQuote = word.contains("'");
            if (word.length() > 3)
                last3 = word.substring(word.length() - 3);
            else
                last3 = word;

            plural = lastIg.contains("A3pl");
            nounCase = "_";
            for (String aCase : cases) {
                if (lastIg.contains(aCase)) {
                    nounCase = aCase;
                    break;
                }
            }
        }

        static Set<String> cases = Sets.newHashSet("Dat", "Abl", "Acc", "Inst", "Loc", "Gen", "Nom");

        public TurkishChunkFeatures(String word) {
            this.word = word;
            this.lemma = word;
            this.lastIg = "NO_IG";
            this.pos = "_";
            this.secPos = "_";
            this.last3 = "_";
            this.nounCase = "_";
        }

        public String featureString(String delimiter) {
            return Joiner.on(delimiter).join(getFeatureList());
        }

        public String featureString(String delimiter, String label) {
            List<String> features = getFeatureList();
            features.add(label);
            return Joiner.on(delimiter).join(features);
        }

        public List<String> getFeatureList() {
            return Lists.newArrayList(
                    word,
                    lemma,
                    pos,
                    secPos,
                    lastIg,
                    nounCase,
                    last3,
                    String.valueOf(firstLetterCapital),
                    String.valueOf(containsQuote)
            );
        }
    }

    public static void main(String[] args) throws IOException {
        ChunkerAnnotationFeatureExtractor chunkerAnnotationFeatureExtractor = new ChunkerAnnotationFeatureExtractor(" ", false);
        chunkerAnnotationFeatureExtractor.generateFromAnnotationFile(
                new File("data/chunker-annotated.txt"),
                new File("data/chunk-single-features.txt")
        );
        CrfTemplates templates = CrfTemplates.loadFromCrfPlusPlusTemplate(new File("crfplusplus/template_cemil"), "/");
        templates.generateFullFeatures(
                new File("data/chunk-single-features.txt"),
                new File("data/chunk-full-features.txt"), " ");

    }


}
