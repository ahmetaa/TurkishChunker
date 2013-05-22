package trnlp.chunking;

import com.google.common.base.Joiner;
import org.jcaki.SimpleTextWriter;
import org.jcaki.Strings;
import trnlp.apps.CrfPlusPlusRunner;
import trnlp.apps.CrfTemplates;
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
    CrfTemplates templates;

    public CrfPlusPlusBasedChunker(File model, File templateFile) throws IOException {
        this.runner = new CrfPlusPlusRunner(model);
        this.templates = CrfTemplates.loadFromCrfPlusPlusTemplate(templateFile);
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

    public void createFeatureFile(SentenceMorphParse input, File file) throws IOException {
        List<ChunkerFeatureExtractor.TurkishChunkFeatures> featuresList = new ArrayList<>();

        for (SentenceMorphParse.Entry entry : input) {
            MorphParse first = entry.parses.get(0);
            featuresList.add(new ChunkerFeatureExtractor.TurkishChunkFeatures(entry.input, first));
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < featuresList.size(); i++) {
            List<String> fullFeatures = featuresList.get(i).getFeatureList();
            lines.add(Joiner.on("\t").join(connectecFeatureList));
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

        CrfPlusPlusBasedChunker chunker = new CrfPlusPlusBasedChunker(
                new File("crfplusplus/cemil_model"),
                new File("crfplusplus/template_cemil"));
        System.out.println(chunker.getChunks(tokenList, disambiguated));
    }
}
