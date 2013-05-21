package trnlp.apps;

import com.google.common.base.Joiner;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CrfPlusPlusRunner {

    File modelFile;

    public CrfPlusPlusRunner(File modelFile) {
        this.modelFile = modelFile;
    }

    public List<String> findLabels(File input) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                "crf_test",
                "-m",
                modelFile.getAbsolutePath(),
                input.getAbsolutePath());

        List<String> result = new ArrayList<>();
        Process p = builder.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String s;
            while ((s = br.readLine()) != null) {
                String line = s.trim();
                if (line.length() == 0) {
                    continue;
                }
                result.add(s.trim());
            }
            p.waitFor();
        }
        return result;
    }

}
