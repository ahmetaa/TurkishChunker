package trnlp.chunking;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import trnlp.apps.CrfTemplates;
import trnlp.apps.TurkishMorphology;
import trnlp.apps.TurkishSentenceTokenizer;
import zemberek3.parser.morphology.SentenceMorphParse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ChunkerTest {

    Chunker chunker;
    TurkishSentenceTokenizer tokenizer = new TurkishSentenceTokenizer();
    TurkishMorphology morphology;

    public ChunkerTest(Chunker chunker) throws IOException {
        this.chunker = chunker;
        this.morphology = new TurkishMorphology();
    }

    public void test(File annotatedTestFile, String featureDelimiter) throws IOException {
        // Extract the reference chunk information from the test.
        ChunkerAnnotationFeatureExtractor extractor = new ChunkerAnnotationFeatureExtractor(featureDelimiter, false);
        List<List<ChunkerAnnotationFeatureExtractor.ChunkData>> reference =
                Lists.newArrayList(extractor.getProperLines(annotatedTestFile));

        // Extract bare sentences for test
        List<String> cleanSentences = new ArrayList<>();
        for (List<ChunkerAnnotationFeatureExtractor.ChunkData> chunks : reference) {
            List<String> allWords = new ArrayList<>();
            for (ChunkerAnnotationFeatureExtractor.ChunkData chunkData : chunks) {
                allWords.add(chunkData.wordsBlock);
            }
            cleanSentences.add(Joiner.on(" ").join(allWords));
        }

        System.out.println();

        // Check the results with the reference.
        int i = 0;
        int hit = 0;
        int total = 0;
        for (String sentence : cleanSentences) {

            List<Chunk> chunks = findChunks(sentence);
            List<String> resultLabels = new ArrayList<>();
            for (Chunk chunk : chunks) {
                for (String s : chunk.words) {
                    resultLabels.add(chunk.type.label);
                }
            }

            List<ChunkerAnnotationFeatureExtractor.ChunkData> referenceChunks = reference.get(i);
            List<String> referenceLabels = new ArrayList<>();
            for (ChunkerAnnotationFeatureExtractor.ChunkData referenceChunk : referenceChunks) {
                for (String s : tokenizer.tokenizeAsStrings(referenceChunk.wordsBlock)) {
                    referenceLabels.add(referenceChunk.tag.label);
                }
            }


            for (int j = 0; j < referenceLabels.size(); j++) {
                String ref = referenceLabels.get(j);
                if (ref.equals(resultLabels.get(j))) {
                    hit++;
                }
                total++;
            }
            i++;
        }

        System.out.println("Total : " + total);
        System.out.println("Hit   : " + hit);
        System.out.format("%.2f", (double) hit / (double) total);
    }

    List<Chunk> findChunks(String input) {
        // Run the test.
        String tokenized = tokenizer.getTokensContentsAsString(input);
        List<String> tokenList = tokenizer.tokenizeAsStrings(input);
        SentenceMorphParse disambiguated = morphology.parseAndDisambiguateSentence(tokenized);
        return chunker.getChunks(tokenList, disambiguated);
    }


    public static void main(String[] args) throws IOException {
        Chunker malletChunker = new MalletBasedChunker(
                new File("src/tr/models/chunk.ser"),
                CrfTemplates.loadFromCrfPlusPlusTemplate(new File("crfplusplus/template_cemil"), "/"));

        Chunker crfPlusPlusBasedChunker = new CrfPlusPlusBasedChunker(new File("data/chunk-model-crfpp.ser"));

        ChunkerTest chunkerTest = new ChunkerTest(malletChunker);
        chunkerTest.test(new File("data/chunker-test.txt"), " ");
        ChunkerTest chunkerTest2 = new ChunkerTest(crfPlusPlusBasedChunker);
        chunkerTest2.test(new File("data/chunker-test.txt"), " ");
    }

}
