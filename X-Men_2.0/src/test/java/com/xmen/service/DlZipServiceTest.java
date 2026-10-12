package com.xmen.service;

import static org.junit.jupiter.api.Assertions.*;
import com.xmen.model.ParametersBundle;
import com.xmen.utilities.FileHandler;
import com.xmen.utilities.MutatedFileGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DlZipServiceTest {
  @Test
  void concurrentArchivesKeepModelsAndCognitiveTracesInTheirOwnRequest() throws Exception {
    MutatedFileGenerator renderer = new MutatedFileGenerator();
    ReflectionTestUtils.setField(renderer, "fileHandler", new FileHandler());
    ZipService service = new ZipService();
    ReflectionTestUtils.setField(service, "mutatedFileGenerator", renderer);
    // No shared workspace is injected: DL archiving must not touch disk at all.
    var first = CompletableFuture.supplyAsync(() -> service.createDlZipResponse(bundle("Alpha")));
    var second = CompletableFuture.supplyAsync(() -> service.createDlZipResponse(bundle("Beta")));
    Map<String, String> a = unzip(first.get().getBody().getByteArray());
    Map<String, String> b = unzip(second.get().getBody().getByteArray());
    assertEquals(2, a.size());
    assertTrue(a.get("same_M0.m").contains("theory Alpha"));
    assertTrue(b.get("same_M0.m").contains("theory Beta"));
    assertTrue(a.get("same_CognitiveTrace.txt").contains("trace Alpha"));
    assertFalse(a.get("same_CognitiveTrace.txt").contains("original source"));
    assertTrue(b.get("same_CognitiveTrace.txt").contains("trace Beta"));
  }

  private static ParametersBundle bundle(String theory) {
    var b = new ParametersBundle();
    b.setFileName("../../same.spthy");
    b.setCollections(new ArrayList<>(List.of(new ArrayList<>())));
    b.setFunctions(new ArrayList<>());
    b.setBuiltins(new ArrayList<>());
    b.addExtraContent("preamble", "theory " + theory + "\nbegin");
    b.addExtraContent("postamble", "end");
    b.addExtraContent("forget.dl.trace.0", "trace " + theory);
    b.addExtraContent("forget.dl.source", "original source");
    return b;
  }

  private static Map<String, String> unzip(byte[] bytes) throws Exception {
    Map<String, String> entries = new LinkedHashMap<>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return entries;
  }
}
