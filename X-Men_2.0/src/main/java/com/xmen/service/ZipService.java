package com.xmen.service;

import com.xmen.utilities.MutationWorkspace;
import com.xmen.utilities.MutatedFileGenerator;
import com.xmen.model.ParametersBundle;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service for creating ZIP files containing generated mutation files.
 */
@Service
@Slf4j
public class ZipService {

  @Autowired private MutationWorkspace workspace;
  @Autowired private MutatedFileGenerator mutatedFileGenerator;

  /** DL models and cognitive traces never pass through the shared filesystem workspace. */
  public ResponseEntity<ByteArrayResource> createDlZipResponse(ParametersBundle bundle) {
    if (bundle.getCollections().isEmpty()) {
      throw new IllegalArgumentException("Forget-DL produced no conclusive ceremony variants");
    }
    String filename = bundle.getFileName().replace('\\', '/');
    filename = filename.substring(filename.lastIndexOf('/') + 1);
    int extension = filename.lastIndexOf('.');
    String base = (extension > 0 ? filename.substring(0, extension) : filename)
        .replaceAll("[^a-zA-Z0-9_.-]", "_");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (int i = 0; i < bundle.getCollections().size(); i++) {
        zip.putNextEntry(new ZipEntry(base + "_M" + i + ".m"));
        zip.write(mutatedFileGenerator.render(bundle, bundle.getCollections().get(i))
            .getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
      }
      StringBuilder trace = new StringBuilder();
      bundle.getExtraContent().entrySet().stream()
          .filter(e -> e.getKey().startsWith("forget.dl.") && !e.getKey().equals("forget.dl.source"))
          .sorted(java.util.Map.Entry.comparingByKey())
          .forEach(e -> trace.append(e.getKey()).append('\n').append(e.getValue()).append('\n'));
      zip.putNextEntry(new ZipEntry(base + "_CognitiveTrace.txt"));
      zip.write(trace.toString().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
    } catch (IOException e) {
      throw new IllegalStateException("Could not create Forget-DL archive", e);
    }
    ResponseEntity.BodyBuilder response = ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + base + "_mutations.zip\"")
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .contentLength(bytes.size());
    if (bundle.isVariantsTruncated()) response.header("X-Variants-Truncated", "true");
    return response.body(new ByteArrayResource(bytes.toByteArray()));
  }

  /**
   * Creates a ZIP file containing all files matching the pattern and returns it as a ResponseEntity.
   *
   * @param baseFileName The base name of the original file (without extension)
   * @return ResponseEntity containing the ZIP file as a ByteArrayResource
   */
  public ResponseEntity<ByteArrayResource> createZipResponse(String baseFileName) {
    return createZipResponse(baseFileName, null);
  }

  /**
   * Creates a ZIP file containing mutation files and optionally derivation tree.
   *
   * @param baseFileName The base name of the original file (without extension)
   * @param derivationTreeContent Optional derivation tree content to include
   * @return ResponseEntity containing the ZIP file as a ByteArrayResource
   */
  public ResponseEntity<ByteArrayResource> createZipResponse(String baseFileName, String derivationTreeContent) {
    try {
      // Find all generated mutation files — these live under the shared
      // MutationWorkspace root, not the process CWD (CWD is "/" under the
      // jpackage-built .app / .deb and was the reason the macOS / Linux
      // installer always shipped an empty zip).
      File directory = workspace.root().toFile();
      File[] mutationFiles = directory.listFiles((dir, name) ->
          name.startsWith(baseFileName + "_M") && name.endsWith(".m"));

      if (mutationFiles == null || mutationFiles.length == 0) {
        log.warn("No mutation files found for base name: {}", baseFileName);
        return ResponseEntity.noContent().build();
      }

      // Create ZIP in memory
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (ZipOutputStream zos = new ZipOutputStream(baos)) {

        // Add derivation tree file if provided
        if (derivationTreeContent != null && !derivationTreeContent.trim().isEmpty()) {
          try {
            String derivationFileName = baseFileName + "_DerivationTree.txt";
            ZipEntry derivationEntry = new ZipEntry(derivationFileName);
            zos.putNextEntry(derivationEntry);
            zos.write(derivationTreeContent.getBytes());
            zos.closeEntry();
            log.info("Added derivation tree to ZIP: {}", derivationFileName);
          } catch (IOException e) {
            log.error("Error adding derivation tree to ZIP: {}", e.getMessage());
          }
        }

        for (File file : mutationFiles) {
          try {
            // Add file to ZIP
            ZipEntry entry = new ZipEntry(file.getName());
            zos.putNextEntry(entry);

            byte[] fileContent = Files.readAllBytes(file.toPath());
            zos.write(fileContent);
            zos.closeEntry();

            log.debug("Added file to ZIP: {}", file.getName());
          } catch (IOException e) {
            log.error("Error adding file {} to ZIP: {}", file.getName(), e.getMessage());
          }
        }
      }

      // Create response
      byte[] zipData = baos.toByteArray();
      ByteArrayResource resource = new ByteArrayResource(zipData);

      String zipFileName = baseFileName + "_mutations.zip";

      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFileName + "\"")
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .contentLength(zipData.length)
          .body(resource);

    } catch (Exception e) {
      log.error("Error creating ZIP file: {}", e.getMessage(), e);
      return ResponseEntity.internalServerError().build();
    }
  }
}
