package trnlp.apps;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.jcaki.SimpleTextReader;
import org.jcaki.SimpleTextWriter;
import org.jcaki.Strings;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class CrfTemplates {

    List<List<TemplateItem>> templateLists;
    String compoundFeatureDelimiter = "/";


    public static void main(String[] args) throws IOException {
        CrfTemplates.loadFromCrfPlusPlusTemplate(new File("crfplusplus/template_cemil"));
    }

    public CrfTemplates(List<List<TemplateItem>> templateLists) {
        this.templateLists = templateLists;
    }

    public CrfTemplates(List<List<TemplateItem>> templateLists, String compoundFeatureDelimiter) {
        this.templateLists = templateLists;
        this.compoundFeatureDelimiter = compoundFeatureDelimiter;
    }

    /**
     * Loads template information from CRF++ template file.
     *
     * @param templateFile CRF++ formatted template file.
     * @return CrfTemplates instance.
     * @throws IOException
     */
    public static CrfTemplates loadFromCrfPlusPlusTemplate(File templateFile) throws IOException {
        List<String> allLines = SimpleTextReader.trimmingUTF8Reader(templateFile).asStringList();
        Set<String> ids = new HashSet<>();
        List<List<TemplateItem>> result = new ArrayList<>();
        for (String line : allLines) {
            if (line.startsWith("#"))
                continue;
            if (!line.contains(":")) {
                System.out.println("Line does not contains [:] character " + line);
            }
            String id = Strings.subStringUntilFirst(line, ":");
            if (ids.contains("id")) {
                System.out.println("Template with id: " + id + " has already been defined. Line: " + line);
            }
            List<TemplateItem> templateItems = new ArrayList<>(2);
            String templateString = Strings.subStringAfterFirst(line, ":").replaceAll("[\\[\\]%x]+", "");
            boolean noError = true;
            for (String data : Splitter.on("/").split(templateString)) {
                TemplateItem item = TemplateItem.get(data);
                if (item == null) {
                    noError = false;
                    break;
                }
                templateItems.add(item);
            }
            if (!noError) {
                System.out.println("Cannot parse line: " + line);
            }
            ids.add(id);
            result.add(templateItems);
        }
        return new CrfTemplates(result);
    }

    //
    public List<List<SingleWordFeature>> getSingleFeatures(File singleWordFeatureFile, String featureDelimiter) throws IOException {
        List<String> all = new SimpleTextReader(singleWordFeatureFile, "utf-8").asStringList();
        List<List<SingleWordFeature>> result = new ArrayList<>();

        List<SingleWordFeature> sentenceLines = new ArrayList<>();

        for (String s : all) {
            if (s.trim().length() == 0) {
                if (sentenceLines.size() > 0) {
                    result.add(sentenceLines);
                    sentenceLines = new ArrayList<>();
                }
                continue;
            }
            List<String> tokens = Lists.newArrayList(Splitter.on(featureDelimiter).split(s));
            if (tokens.size() < 3) {
                throw new RuntimeException("Error in line:" + s + ". At least 3 tokens expected.");
            }
            sentenceLines.add(new SingleWordFeature(tokens));
        }
        if (sentenceLines.size() > 0) {
            result.add(sentenceLines);
        }

        return result;
    }

    public static class SingleWordFeature {
        List<String> features = new ArrayList<>();
        String label;

        SingleWordFeature(List<String> tokens) {
            this.label = tokens.get(tokens.size() - 1);
            this.features = Lists.newArrayList(tokens.subList(0, tokens.size() - 1));
        }

        public String asFeatureLine(String delimiter) {
            return Joiner.on(delimiter).join(features) + delimiter + label;
        }

        public String toString() {
            return asFeatureLine(" ");
        }

    }

    List<SingleWordFeature> getFullFeatures(List<SingleWordFeature> sentenceSingleFeatures) {
        List<SingleWordFeature> result = new ArrayList<>();
        int k = 0;
        for (SingleWordFeature sentenceSingleFeature : sentenceSingleFeatures) {
            List<String> features = new ArrayList<>();
            for (List<TemplateItem> templateList : templateLists) {
                List<String> feature = new ArrayList<>();
                for (TemplateItem templateItem : templateList) {
                    int pos = templateItem.position + k;
                    if (pos < 0 || pos >= sentenceSingleFeatures.size()) {
                        feature.add("_");
                        continue;
                    }
                    feature.add(sentenceSingleFeatures.get(pos).features.get(templateItem.featureIndex));
                }
                features.add(Joiner.on(compoundFeatureDelimiter).join(feature));
            }
            k++;
            features.add(sentenceSingleFeature.label);
            result.add(new SingleWordFeature(features));
        }
        return result;
    }

    public void generateFullFeatures(File inputFile, File outputFile, String featureDelimiter) throws IOException {
        SimpleTextWriter writer = SimpleTextWriter.keepOpenUTF8Writer(outputFile);
        List<List<SingleWordFeature>> allSingleFeatures = getSingleFeatures(inputFile, featureDelimiter);
        int i = 0;
        for (List<SingleWordFeature> singleFeatures : allSingleFeatures) {
            List<SingleWordFeature> fullFeature = getFullFeatures(singleFeatures);
            for (SingleWordFeature singleWordFeature : fullFeature) {
                writer.writeLine(singleWordFeature.asFeatureLine(featureDelimiter));
            }
            if (i < allSingleFeatures.size() - 1)
                writer.writeLine();
            i++;
        }
        writer.close();
    }


    public static class TemplateItem {
        int position;
        int featureIndex;

        TemplateItem(int position, int featureIndex) {
            this.position = position;
            this.featureIndex = featureIndex;
        }

        static TemplateItem get(String s) {
            try {
                int position = Integer.parseInt(Strings.subStringUntilFirst(s, ",").trim());
                int fe = Integer.parseInt(Strings.subStringAfterFirst(s, ",").trim());
                return new TemplateItem(position, fe);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return null;
            }
        }

        public String toString() {
            return position + ":" + featureIndex;
        }
    }

}
