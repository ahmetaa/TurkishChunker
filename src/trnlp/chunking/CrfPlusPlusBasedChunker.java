package trnlp.chunking;

import com.google.common.base.Joiner;
import org.jcaki.SimpleTextWriter;
import org.jcaki.Strings;
import trnlp.apps.CrfPlusPlusRunner;
import trnlp.apps.TurkishMorphology;
import trnlp.apps.TurkishSentenceTokenizer;
import zemberek3.parser.morphology.MorphParse;
import zemberek3.parser.morphology.SentenceMorphParse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CrfPlusPlusBasedChunker extends Chunker {

    CrfPlusPlusRunner runner;

    public CrfPlusPlusBasedChunker(File model) throws IOException {
        this.runner = new CrfPlusPlusRunner(model);
    }

    public List<Chunk> getChunks(List<String> words, SentenceMorphParse input) {
        try {
            File tmp = File.createTempFile("trchunker", ".txt");
            tmp.deleteOnExit();
            createFeatureFile(input, tmp);
            List<String> lines = runner.findLabels(tmp);
            List<String> labels = new ArrayList<>();
            for (String line : lines) {
                labels.add(Strings.subStringAfterLast(line, "\t"));
            }
            return getChunks(words, labels, input);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    /**
     * Generates feature file for a single sentence. Because CRF++ model already contains the feature template
     * information, we do not need to give full feature information. Only single word features are enough.
     *
     * @param input morphological parse of the input sentence.
     * @param file  output file that will contain the feature information. This file will be passed to CRF++ process.
     * @throws IOException
     */
    public void createFeatureFile(SentenceMorphParse input, File file) throws IOException {
        List<ChunkerAnnotationFeatureExtractor.TurkishChunkFeatures> featuresList = new ArrayList<>();

        for (SentenceMorphParse.Entry entry : input) {
            MorphParse first = entry.parses.get(0);
            featuresList.add(new ChunkerAnnotationFeatureExtractor.TurkishChunkFeatures(entry.input, first));
        }

        List<String> lines = new ArrayList<>();
        for (ChunkerAnnotationFeatureExtractor.TurkishChunkFeatures features : featuresList) {
            lines.add(Joiner.on("\t").join(features.getFeatureList()));
        }

        SimpleTextWriter.oneShotUTF8Writer(file).writeLines(lines);
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        TurkishSentenceTokenizer tokenizer = new TurkishSentenceTokenizer();
        TurkishMorphology morphology = new TurkishMorphology();
        //String input = "Ahmet öğleden sonra çay demledi.";

        String input = "Ahmet öğleden sonra çay demledi.";

        String tokenized = tokenizer.getTokensContentsAsString(input);

        List<String> tokenList = tokenizer.tokenizeAsStrings(input);

        SentenceMorphParse parse = morphology.parseSentence(tokenized);
        for (SentenceMorphParse.Entry entry : parse) {
            System.out.println(entry.parses);
        }

        SentenceMorphParse disambiguated = morphology.parseAndDisambiguateSentence(tokenized);
        for (SentenceMorphParse.Entry entry : disambiguated) {
            System.out.println(entry.parses);
        }

        CrfPlusPlusBasedChunker chunker = new CrfPlusPlusBasedChunker(new File("crfplusplus/cemil_model"));
        System.out.println(chunker.getChunks(tokenList, disambiguated));
    }
}
