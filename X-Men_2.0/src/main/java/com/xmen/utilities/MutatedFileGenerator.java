package com.xmen.utilities;

import com.xmen.model.ParametersBundle;
import com.xmen.model.Rule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

/**
 * MutatedFileGenerator class. This class is responsible for generating mutated files based on the
 * provided parameters bundle. It saves the mutated rules into files with a specific naming
 * convention, ensuring that existing mutated files are deleted before saving new ones.
 */
@Component
@Slf4j
public class MutatedFileGenerator {

  @Autowired private FileHandler fileHandler;
  @Autowired private MutationWorkspace workspace;

  /**
   * Saves the mutated files based on the provided parameters bundle.
   *
   * @param parametersBundle ParametersBundle containing the necessary data to generate the files.
   */
  public void saveFiles(ParametersBundle parametersBundle) {
    deleteExistingMutatedFiles();

    String filename = parametersBundle.getFileName();
    ArrayList<ArrayList<Rule>> collections = parametersBundle.getCollections();
    String baseName = filename.split("\\.(?=[^\\.]+$)")[0];
    int fileCounter = 0;

    for (ArrayList<Rule> model : collections) {
      String newFileName = baseName + "_M" + fileCounter + ".m";
      // Always resolve through MutationWorkspace so we land in a guaranteed
      // writable directory (~/.xmen/runs by default) rather than the process
      // CWD, which is "/" under jpackage'd .app and .deb bundles.
      Path outputPath = workspace.resolve(newFileName);

      try (BufferedWriter writer =
          Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
        writer.write(render(parametersBundle, model));

        fileCounter++;
      } catch (IOException e) {
        log.error("Error saving file {}: {}", newFileName, e.getMessage());
      }
    }
  }

  /** Shared serialization for disk output and request-local DL archives. */
  public String render(ParametersBundle bundle, ArrayList<Rule> model) {
    model = fileHandler.demergeTagsValues(model, bundle);
    StringBuilder output = new StringBuilder(bundle.getExtraContent("preamble")).append("\n\n");
    if (!bundle.getFunctions().isEmpty()) {
      output.append("functions: ");
      for (int i = 0; i < bundle.getFunctions().size(); i++) {
        if (i > 0) output.append(',');
        output.append(bundle.getFunctions().get(i));
      }
      output.append("\n\n");
    }
    if (!bundle.getBuiltins().isEmpty()) {
      output.append(bundle.getBuiltins().get(0)).append("\n\n");
    }
    model.forEach(output::append);
    return output.append('\n').append(bundle.getExtraContent("postamble")).toString();
  }

  /** Deletes existing mutated files from the mutation workspace. */
  public void deleteExistingMutatedFiles() {
    File directory = workspace.root().toFile();
    File[] files = directory.listFiles((dir, name) -> name.contains("_M") && name.endsWith(".m"));

    if (files != null) {
      for (File file : files) {
        if (!file.delete()) {
          log.error("Failed to delete file: {}", file.getName());
        }
      }
    }
  }
}
