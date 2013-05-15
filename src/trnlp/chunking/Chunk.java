package trnlp.chunking;

import com.google.common.base.Joiner;
import zemberek3.parser.morphology.MorphParse;

import java.util.List;

public class Chunk {
    int index;
    ChunkerFeatureExtractor.ChunkType type;
    List<MorphParse> parses;
    List<String> words;

    public Chunk(int index, ChunkerFeatureExtractor.ChunkType type, List<MorphParse> parses, List<String> words) {
        this.index = index;
        this.type = type;
        this.parses = parses;
        this.words = words;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        sb.append(Joiner.on(" ").join(words));
        sb.append(":").append(type.name()).append("]");
        return sb.toString();
    }

    public String explain() {
        StringBuilder sb = new StringBuilder("[");
        int k = 0;
        for (MorphParse parse : parses) {
            sb.append(parse.dictionaryItem.lemma);
            if (k < parses.size() - 1)
                sb.append(" ");
            k++;
        }
        sb.append(":").append(type.name()).append("-").append(index).append("]");
        return sb.toString();
    }
}
