package trnlp.chunking;

import trnlp.apps.ContentPreprocessor;
import trnlp.apps.TurkishMorphology;
import trnlp.apps.TurkishSentenceTokenizer;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.jcaki.SimpleTextReader;
import org.jcaki.Strings;
import zemberek3.parser.morphology.MorphParse;
import zemberek3.parser.morphology.SentenceMorphParse;
import zemberek3.shared.lexicon.SecondaryPos;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class ChunkerTrainer {

    Set<String> crfTags = Sets.newHashSet("OB", "OI", "TB", "TI", "YB", "YI");
    ContentPreprocessor preprocessor = new ContentPreprocessor();
    TurkishSentenceTokenizer tokenizer = new TurkishSentenceTokenizer();
    TurkishMorphology morphology;

    String delimiter = " ";

    public ChunkerTrainer() throws IOException {
        this.morphology = new TurkishMorphology();
    }

    public ChunkerTrainer(String delimiter) throws IOException {
        this.morphology = new TurkishMorphology();
        this.delimiter = delimiter;
    }

    public void generateFromAnnotationFile(File in, File out) throws IOException {
        PrintWriter pw = new PrintWriter(out, "utf-8");

        // load all sentences
        List<String> lines = SimpleTextReader.trimmingUTF8Reader(in).asStringList();

        // eliminate duplicated sentences and give statistics
        Set<List<ChunkData>> accepted = new LinkedHashSet<>();
        Set<String> declined = new LinkedHashSet<>();
        for (String line : lines) {
            line = line.replaceAll("[*]", "");
            if (accepted.contains(line)) {
                System.out.println("Duplicated line: " + line);
            } else {
                List<ChunkData> chunks = new ArrayList<>();
                // split from chunks
                for (String s : Splitter.on("/").trimResults().omitEmptyStrings().split(line)) {
                    ChunkData chunkData = ChunkData.generate(s);
                    if (chunkData == null) {
                        System.out.println("Bad chunk detected:[" + s + "]                in sentence:" + line);
                        chunks.clear();
                        break;
                    } else
                        chunks.add(chunkData);
                }
                if (chunks.isEmpty()) {
                    declined.add(line);
                } else
                    accepted.add(chunks);
            }
        }

        System.out.println("Total Sentence count:" + lines.size());
        System.out.println("Accepted Line count:" + accepted.size());
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
            SentenceMorphParse parse = null;
            try {
                parse = morphology.parseAndDisambiguateSentence(allSentence);
            } catch (Exception e) {
                System.out.println("Error during morphological parse of sentence:" + allSentence + " with exception:" + e.getMessage());
            }

            if (parse == null) {
                continue;
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
            TurkishChunkFeatures prev;
            TurkishChunkFeatures next;

            for (int j = 0; j < wordFeatures.size(); j++) {
                if (j > 0) {
                    prev = wordFeatures.get(j - 1).features;
                } else {
                    prev = new WordFeature("X", TurkishChunkFeatures.START).features;
                }
                if (j < wordFeatures.size() - 1) {
                    next = wordFeatures.get(j + 1).features;
                } else {
                    next = new WordFeature("X", TurkishChunkFeatures.END).features;
                }
                TurkishChunkFeatures current = wordFeatures.get(j).features;
                List<String> features = current.getConnectecFeatureList(prev, next);
                //List<String> features = current.getFeatureList();
                String label = wordFeatures.get(j).label;
                features.add(label);
                featureLines.add(Joiner.on(" ").join(features));
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

        static ChunkData generate(String chunk) {
            chunk = chunk.trim();
            String wordsBlock = null;
            ChunkType tag = null;

            for (String annotation : annotationMap.keySet()) {
                if (chunk.endsWith(annotation)) {
                    wordsBlock = Strings.subStringUntilLast(chunk, annotation);
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
        String lastIg;
        String metaLetters;
        boolean firstLetterCapital = false;
        boolean containsQuote = false;
        boolean allCapital = false;
        boolean containsDot = false;

        public static final TurkishChunkFeatures START = new TurkishChunkFeatures("<s>");
        public static final TurkishChunkFeatures END = new TurkishChunkFeatures("<s>");

        TurkishChunkFeatures(String word, MorphParse parse) {
            this.word = word;
            this.lemma = parse.dictionaryItem.lemma;
            this.pos = parse.getPos().shortForm;
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
            this.containsDot = word.length() > 1 && word.contains(".");

            this.allCapital = isAllUpper(word);
            this.containsQuote = word.contains("'");
            this.metaLetters = metaLetters(word);
        }

        public String metaLetters(String input) {
            StringBuilder sb = new StringBuilder();
            char last = 'x';
            for (int i = 0; i < input.length(); i++) {
                char ch = input.charAt(i);
                if (Character.isUpperCase(ch))
                    ch = 'C';
                else if (Character.isLowerCase(ch))
                    ch = 'c';
                else if (Character.isDigit(ch))
                    ch = 'D';
                else
                    ch = 'P';
                if (ch == last)
                    continue;
                sb.append(ch);
                last = ch;
            }
            return sb.toString();
        }

        public TurkishChunkFeatures(String word) {
            this.word = word;
            this.lemma = word;
            this.lastIg = "NO_IG";
            this.metaLetters = "_";
            this.pos = "_";
        }

        private boolean isAllUpper(String input) {
            for (char c : Strings.subStringUntilFirst(input, "'").toCharArray()) {
                if (!Character.isUpperCase(c)) {
                    return false;
                }
            }
            return true;
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
                    lastIg,
                    metaLetters,
                    String.valueOf(firstLetterCapital),
                    String.valueOf(containsQuote),
                    String.valueOf(allCapital),
                    String.valueOf(containsDot)
            );
        }

        public List<String> getConnectecFeatureList(TurkishChunkFeatures prev, TurkishChunkFeatures next) {
            List<String> features = new ArrayList<>();
            features.addAll(getFeatureList());
            features.addAll(Arrays.asList(
                    "-1" + prev.word, "-1" + prev.metaLetters, "-1" + prev.lemma, "-1" + prev.lastIg, "-1" + prev.pos, "-1" + prev.firstLetterCapital, "-1" + prev.containsQuote,
                    "+1" + next.word, "+1" + next.metaLetters, "+1" + next.lemma, "+1" + next.lastIg, "+1" + next.pos, "+1" + next.firstLetterCapital, "+1" + next.containsQuote,
                    pos + "|" + next.pos,
                    prev.pos + "|" + pos,
                    prev.metaLetters + "|" + metaLetters,
                    metaLetters + "|" + next.metaLetters,
                    prev.lastIg + "|" + lastIg,
                    lastIg + "|" + next.lastIg,
                    prev == START ? "true" : "false",
                    firstLetterCapital + "|" + next.firstLetterCapital,
                    prev.firstLetterCapital + "|" + firstLetterCapital,
                    containsQuote + "|" + next.containsQuote,
                    prev.containsQuote + "|" + containsQuote
            ));
            return features;
        }
    }

    public static void main(String[] args) throws IOException {
        ChunkerTrainer chunkerTrainer = new ChunkerTrainer();
        chunkerTrainer.generateFromAnnotationFile(
                new File("data/chunker-annotated.txt"),
                new File("data/chunk-features.txt")
        );

    }
}
