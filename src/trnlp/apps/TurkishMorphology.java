package trnlp.apps;

import zemberek3.ambiguity.Z3MarkovModelDisambiguator;
import zemberek3.apps.TurkishMorphParser;
import zemberek3.apps.TurkishSentenceParser;
import zemberek3.apps.UnidentifiedTokenParser;
import zemberek3.parser.morphology.MorphParse;
import zemberek3.parser.morphology.SentenceMorphParse;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class TurkishMorphology {
    TurkishMorphParser wordParser;
    TurkishSentenceParser sentenceParser;
    UnidentifiedTokenParser unidentifiedTokenParser;

    File dataDir;

    public TurkishMorphology() throws IOException {
        wordParser = TurkishMorphParser.createWithDefaults();
        Z3MarkovModelDisambiguator disambiguator = new Z3MarkovModelDisambiguator();
        sentenceParser = new TurkishSentenceParser(wordParser, disambiguator);
    }

    public List<MorphParse> parseWord(String input) {
        List<MorphParse> parses = wordParser.parse(input);
        if (parses.size() == 0)
            return unidentifiedTokenParser.parse(input);
        return parses;
    }

    public SentenceMorphParse parseSentence(String input) {
        return sentenceParser.parse(input);
    }

    public SentenceMorphParse parseAndDisambiguateSentence(String input) {
        SentenceMorphParse sentenceParse = parseSentence(input);
        sentenceParser.disambiguate(sentenceParse);
        return sentenceParse;
    }
}
