package trnlp.chunking;

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

    public TurkishChunker(File model) throws IOException {
        ObjectInputStream s = new ObjectInputStream(new FileInputStream(model));
        try {
            crf = (CRF) s.readObject();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        s.close();
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
        List<ChunkerFeatureExtractor.TurkishChunkFeatures> featuresList = new ArrayList<>();
        featuresList.add(ChunkerFeatureExtractor.TurkishChunkFeatures.START);

        for (SentenceMorphParse.Entry entry : input) {
            MorphParse first = entry.parses.get(0);
            featuresList.add(new ChunkerFeatureExtractor.TurkishChunkFeatures(entry.input, first));
        }
        featuresList.add(ChunkerFeatureExtractor.TurkishChunkFeatures.END);

        String[][] featureMatrix = new String[featuresList.size() - 2][];
        for (int i = 1; i < featuresList.size() - 1; i++) {
            List<String> connectecFeatureList = featuresList.get(i).getConnectecFeatureList(featuresList.get(i - 1), featuresList.get(i + 1));
            featureMatrix[i - 1] = connectecFeatureList.toArray(new String[connectecFeatureList.size()]);
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
        TurkishChunker chunker = new TurkishChunker(new File("data/chunk-model.ser"));
        System.out.println(chunker.getChunks(tokenList, disambiguated));
    }

}
