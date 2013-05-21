package trnlp.apps;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class CrfTemplatesTest {

    @Test
    public void crfPlusPlusTest() throws IOException {
        CrfTemplates templates = CrfTemplates.loadFromCrfPlusPlusTemplate(
                new File("test/data/crfpp_template_1.txt"));
        templates.generateFullFeatures(
                new File("test/data/single_features_1.txt"),
                new File("test/data/features_out.txt"),
                " "
        );
    }

}
