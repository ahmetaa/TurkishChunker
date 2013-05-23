package trnlp.chunking;

import trnlp.apps.CrfTemplates;
import trnlp.apps.TurkishMorphology;
import trnlp.apps.TurkishSentenceTokenizer;
import cc.mallet.fst.CRF;
import cc.mallet.types.ArraySequence;
import cc.mallet.types.Instance;
import com.google.common.io.Resources;
import zemberek3.parser.morphology.MorphParse;
import zemberek3.parser.morphology.SentenceMorphParse;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;

public class TurkishChunker extends Chunker {

    CRF crf;
    CrfTemplates templates;

    public TurkishChunker(File model, CrfTemplates templates) throws IOException {
        ObjectInputStream s = new ObjectInputStream(new FileInputStream(model));
        try {
            crf = (CRF) s.readObject();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        s.close();
        this.templates = templates;
    }

    public TurkishChunker() throws IOException {
        ObjectInputStream s = new ObjectInputStream(Resources.getResource("tr/models/chunk.model").openStream());
        try {
            crf = (CRF) s.readObject();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        s.close();
    }

    public static class TokenAndLabel {
        public final String token;
        public final String label;

        public TokenAndLabel(String token, String label) {
            this.token = token;
            this.label = label;
        }

        @Override
        public String toString() {
            return token + ':' + label;
        }
    }

    public List<Chunk> getChunks(List<String> words, SentenceMorphParse input) {
        ArraySequence data = getCrfResult(input);
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < data.size(); i++)
            labels.add((String) data.get(i));
        return getChunks(words, labels, input);
    }

    private ArraySequence getCrfResult(SentenceMorphParse input) {
        List<ChunkerAnnotationFeatureExtractor.TurkishChunkFeatures> featuresList = new ArrayList<>();

        for (SentenceMorphParse.Entry entry : input) {
            MorphParse first = entry.parses.get(0);
            featuresList.add(new ChunkerAnnotationFeatureExtractor.TurkishChunkFeatures(entry.input, first));
        }

        List<List<String>> singleFeatures = new ArrayList<>();
        for (ChunkerAnnotationFeatureExtractor.TurkishChunkFeatures features : featuresList) {
            singleFeatures.add(features.getFeatureList());
        }

        String[][] featureMatrix = new String[featuresList.size()][];
        int i = 0;
        for (List<String> fullFeatures : templates.getFeatureLinesForTest(singleFeatures)) {
            featureMatrix[i] = fullFeatures.toArray(new String[fullFeatures.size()]);
        }

        Instance il = new Instance(featureMatrix, "", "", "");
        Instance answer = crf.transduce(il);
        return (ArraySequence) answer.getData();
    }

    public List<TokenAndLabel> getHypothesisLabels(SentenceMorphParse input) {
        ArraySequence data = getCrfResult(input);
        List<TokenAndLabel> labels = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            labels.add(new TokenAndLabel(input.getEntry(i).input, (String) data.get(i)));
        }
        return labels;
    }


    public static void main(String[] args) throws IOException {
        TurkishSentenceTokenizer tokenizer = new TurkishSentenceTokenizer();
        TurkishMorphology morphology = new TurkishMorphology();
        //String input = "Ahmet öğleden sonra çay demledi.";
        String input = "Bu futbolcunun kafa vuruşunda meşin yuvarlak,  kalenin solundan auta gitti.";
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
        TurkishChunker chunker = new TurkishChunker(
                new File("data/chunk-model.ser"),
                CrfTemplates.loadFromCrfPlusPlusTemplate(
                        new File("crfplusplus/template_cemil"), "/")
        );
        System.out.println(chunker.getChunks(tokenList, disambiguated));
    }

}
