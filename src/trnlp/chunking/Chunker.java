package trnlp.chunking;

import com.google.common.collect.Lists;
import zemberek3.parser.morphology.MorphParse;
import zemberek3.parser.morphology.SentenceMorphParse;

import java.util.ArrayList;
import java.util.List;

public abstract class Chunker {

    public List<Chunk> getChunks(List<String> words, List<String> labels, SentenceMorphParse input) {
        List<Chunk> parses = new ArrayList<>();
        List<MorphParse> morphParses = new ArrayList<>(2);
        int index = 0;
        String previousTag = "X";
        String tag = "S";
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            System.out.println(label);
            if (label.length() != 2) {
                System.out.println("Unexpected label:" + label);
                index++;
                continue;
            }
            tag = label.substring(0, 1);
            boolean isBegin = label.charAt(1) == 'B';

            if (i == 0) {
                morphParses.add(input.getEntry(i).parses.get(0));
                previousTag = tag;
                continue;
            }
            if (!tag.equals(previousTag)) {
                if (morphParses.size() > 0) {
                    parses.add(new Chunk(index, ChunkerFeatureExtractor.ChunkType.getByAbbrv(previousTag), morphParses,
                            Lists.newArrayList(words.subList(index, index + morphParses.size()))));
                    morphParses = new ArrayList<>(2);
                    index = i;
                }
                morphParses.add(input.getEntry(i).parses.get(0));
            } else {
                if (isBegin) {
                    if (morphParses.size() > 0) {
                        parses.add(new Chunk(index, ChunkerFeatureExtractor.ChunkType.getByAbbrv(previousTag), morphParses,
                                Lists.newArrayList(words.subList(index, index + morphParses.size()))));
                        morphParses = new ArrayList<>(2);
                    }
                    index = i;
                }
                morphParses.add(input.getEntry(i).parses.get(0));
            }
            previousTag = tag;
        }
        if (!morphParses.isEmpty()) {
            parses.add(new Chunk(index, ChunkerFeatureExtractor.ChunkType.getByAbbrv(tag), morphParses,
                    Lists.newArrayList(words.subList(index, index + morphParses.size()))));
        }
        return parses;
    }

    abstract protected List<Chunk> getChunks(List<String> words, SentenceMorphParse input);

}
