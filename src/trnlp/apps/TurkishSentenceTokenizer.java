package trnlp.apps;

import com.google.common.base.Joiner;
import org.antlr.v4.runtime.Token;
import zemberek3.shared.tokenizer.antlr.TurkishLexer;
import zemberek3.shared.tokenizer.antlr.ZemberekLexer;

import java.util.ArrayList;
import java.util.List;

public class TurkishSentenceTokenizer {

    ZemberekLexer lexer = new ZemberekLexer();

    private List<Token> tokenList(String input) {
        return lexer.tokenizeAll(input);
    }

    public List<String> tokenizeAsStrings(String sentence) {
        List<Token> tokens = tokenList(sentence);
        List<String> strings = new ArrayList<>(tokens.size());
        for (Token token : tokens) {
            strings.add(token.getText());
        }
        return strings;
    }

    public String getTokensContentsAsString(String input) {
        return Joiner.on(" ").join(tokenizeAsStrings(input));
    }

    public String getTokensAsString(String input) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        List<Token> tokens = tokenList(input);
        for (Token token : tokens) {
            sb.append(token.getText()).append(":").append(TurkishLexer.tokenNames[token.getType()]);
            if (i < tokens.size() - 1)
                sb.append(" ");
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        TurkishSentenceTokenizer tokenizer = new TurkishSentenceTokenizer();
        tokenizer.tokenList("Bu(!)[K I.G], ne demek!?");
        List<String> strings = tokenizer.tokenizeAsStrings("Bu(!)[K I.G], ne demek!?.");
        System.out.println(strings);
    }


}
