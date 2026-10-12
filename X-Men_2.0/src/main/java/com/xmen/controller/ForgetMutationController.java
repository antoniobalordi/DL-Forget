package com.xmen.controller;

import com.xmen.model.*;
import com.xmen.service.*;
import com.xmen.service.forget.ForgetContext.BlockingMode;
import com.xmen.service.impl.DerivationModeContext;
import com.xmen.service.impl.ForgetMutationStrategy;
import com.xmen.utilities.ForgetMutationParser;
import com.xmen.utilities.SetupKnowledgeExtractor;
import com.xmen.utilities.TagSetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Controller for forget mutation. */
@RestController
@RequestMapping("/api")
@Tag(name = "Forget Mutations")
@Slf4j
public class ForgetMutationController {

  @Autowired private FileLoadingService fileLoadingService;
  @Autowired private TagSetter tagSetter;
  @Autowired
  @Qualifier("mutationGeneratorServiceImpl")
  MutationGeneratorService mutationGeneratorService;
  @Autowired private FileSplitterService fileSplitterService;
  @Autowired private SetupKnowledgeExtractor setupKnowledgeExtractor;
  @Autowired private ZipService zipService;
  @Autowired private HaskellDerivationFetcher haskellDerivationFetcher;
  @Autowired private DerivationTreeCaptureService derivationTreeCaptureService;
  @Autowired private ForgetMutationStrategy forgetMutationStrategy;
  @Autowired private com.xmen.service.forget.ForgetDerivationChecker forgetDerivationChecker;

  /**
   * Trigger of forget mutation.
   *
   * @param file The file to process.
   * @param haskellActivate Optional header to activate Haskell derivation service
   * @return A ResponseEntity containing the zipped mutation files.
   */
  @Operation(
      summary = "Generate forget mutations",
      description =
          "Creates forget mutation variants and optionally captures derivation trees. "
              + "Headers can tune derivation mode, maximum variants, blocking mode, "
              + "and witness actions.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Zipped mutation bundle (optionally with derivation tree)",
        content =
            @Content(
                mediaType = "application/zip",
                schema = @Schema(type = "string", format = "binary"))),
    @ApiResponse(responseCode = "400", description = "Invalid input"),
    @ApiResponse(responseCode = "500", description = "Unexpected server error")
  })
  @PostMapping("/forget/mutations")
  public ResponseEntity<?> forgetMutations(
      @Parameter(description = "SPTHY input file", required = true)
      @RequestParam("file") MultipartFile file,
      @Parameter(description = "Enable Haskell derivation if available")
      @RequestHeader(value = "Haskell-Activate", required = false) Boolean haskellActivate,
      @Parameter(description = "Derivation type (e.g., DEPTH_SPECIFIED)")
      @RequestHeader(value = "Derivation-Type", required = false) String derivationType,
      @Parameter(description = "Derivation depth when derivation type requires it")
      @RequestHeader(value = "Derivation-Depth", required = false) Integer derivationDepth,
      @Parameter(description = "Max variants per rule (1-1000)")
      @RequestHeader(value = "Max-Variants-Per-Rule", required = false) Integer maxVariantsPerRule,
      @Parameter(description = "Blocking mode: CASE1/CASE2/CASE3 or aliases")
      @RequestHeader(value = "Blocking-Mode", required = false) String blockingModeHeader,
      @Parameter(description = "Comma-separated witness action names")
      @RequestHeader(value = "Witness-Actions", required = false) String witnessActionsHeader,
      @Parameter(description = "Forget semantics: ALGORITHM1 (default) or DL (Algorithm 2)")
      @RequestHeader(value = "Forget-Semantics", required = false) String forgetSemantics)
      throws Exception {
    boolean haskellWasEnabled = false;
    String derivationTreeContent = null;
    ParametersBundle parametersBundle = new ParametersBundle();
    parametersBundle.setDefeasibleLogicMode(false);

    try {
      if (maxVariantsPerRule != null) {
        if (maxVariantsPerRule <= 0 || maxVariantsPerRule > 1000) {
          return ResponseEntity.status(400)
              .body("Max-Variants-Per-Rule must be between 1 and 1000");
        }
        parametersBundle.setForgetMaxVariantsPerRule(maxVariantsPerRule);
      }

      if (forgetSemantics != null) {
        String s = forgetSemantics.trim().toUpperCase(Locale.ROOT);
        if (s.equals("DL") || s.equals("DEFEASIBLE") || s.equals("ALGORITHM2")) {
          parametersBundle.setDefeasibleLogicMode(true);
        } else if (!s.equals("ALGORITHM1") && !s.isEmpty()) {
          return ResponseEntity.status(400).body("Unknown Forget-Semantics: " + forgetSemantics);
        }
      }

      if (blockingModeHeader != null) {
        try {
          BlockingMode parsedMode = parseBlockingMode(blockingModeHeader);
          if (parsedMode != null) {
            parametersBundle.setForgetBlockingMode(parsedMode);
          }
        } catch (IllegalArgumentException e) {
          return ResponseEntity.status(400).body(e.getMessage());
        }
      }

      // Honour the user's Derivation-Type selection so the Forget pipeline
      // actually respects "Infinite" / "Specified Depth". Without this the
      // ForgetDerivationChecker always silently used its built-in depth=10
      // and max=50 caps regardless of what the UI requested.
      if (!parametersBundle.getDefeasibleLogicMode() && "INFINITE".equalsIgnoreCase(derivationType)) {
        forgetDerivationChecker.overrideLimits(Integer.MAX_VALUE, Integer.MAX_VALUE);
      } else if (!parametersBundle.getDefeasibleLogicMode() && "DEPTH_SPECIFIED".equalsIgnoreCase(derivationType)
          && derivationDepth != null
          && derivationDepth > 0) {
        forgetDerivationChecker.overrideLimits(derivationDepth, Integer.MAX_VALUE);
      }

      if (witnessActionsHeader != null && !witnessActionsHeader.trim().isEmpty()) {
        Set<String> witnesses = Arrays.stream(witnessActionsHeader.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
        for (String name : witnesses) {
          if (!IDENTIFIER_PATTERN.matcher(name).matches()) {
            return ResponseEntity.status(400)
                .body("Witness-Actions entries must be identifier-like (letters, digits, underscore)");
          }
        }
        parametersBundle.setForgetWitnessActions(witnesses);
      }

      // Start capturing derivation tree output
      if (!parametersBundle.getDefeasibleLogicMode()) derivationTreeCaptureService.startCapture();

      // Only FORGET mutation
      Set<Mutations> mutationSet = EnumSet.of(Mutations.FORGET);

      // Process file content
      String fileContent = new String(file.getBytes(), StandardCharsets.UTF_8);
      if (parametersBundle.getDefeasibleLogicMode()) {
        validateDlSource(fileContent);
        parametersBundle.addExtraContent("forget.dl.source", fileContent);
      }
      FileSplitterService.FileSections sections = fileSplitterService.splitFile(fileContent);
      if (parametersBundle.getDefeasibleLogicMode()) validateDlRules(sections.rules());

      // Create virtual MultipartFile for rules section
      MultipartFile rulesFile =
          new InMemoryMultipartFile(
              "rulesFile",
              file.getOriginalFilename().replace(".spthy", "_rules.spthy"),
              "text/plain",
              sections.rules().getBytes(StandardCharsets.UTF_8));

      // Set tags and load rules
      parametersBundle.setFlags(new Flags());
      parametersBundle = tagSetter.setTags(parametersBundle, mutationSet);
      parametersBundle = fileLoadingService.fileLoader(rulesFile, parametersBundle);

      // Store file sections
      parametersBundle.addExtraContent("preamble", sections.preamble());
      parametersBundle.addExtraContent("postamble", sections.postamble());

      // Parse forget mutations and set flag (mirror MutationController behavior)
      ArrayList<Rule> originalRules = parametersBundle.getCollections().get(0);

      boolean shouldUseHaskell = !parametersBundle.getDefeasibleLogicMode() && Boolean.TRUE.equals(haskellActivate);
      if (shouldUseHaskell && !haskellDerivationFetcher.isServiceAvailable()) {
        shouldUseHaskell = false;
        log.warn("Haskell-Activate header set but service unavailable; staying on Java path");
      }

      if (shouldUseHaskell) {
        log.info("Haskell-Activate header detected, enabling HybridDerivationService");
        try {
          String theoryName = file.getOriginalFilename().replace(".spthy", "");
          com.xmen.service.impl.HybridDerivationService.enableHaskellDerivation(
              originalRules, theoryName);
          DerivationModeContext.enableHaskell();
          haskellWasEnabled = true;

          log.info("Haskell derivation ENABLED for theory: {}", theoryName);
        } catch (Exception e) {
          log.error("Error enabling Haskell derivation: {}", e.getMessage());
          com.xmen.service.impl.HybridDerivationService.disableHaskellDerivation();
          DerivationModeContext.disableHaskell();
        }
      } else if (!parametersBundle.getDefeasibleLogicMode()) {
        com.xmen.service.impl.HybridDerivationService.disableHaskellDerivation();
        DerivationModeContext.disableHaskell();
        haskellWasEnabled = false;
      }

      // Continue with standard forget mutation processing
      // Extract setup knowledge to support propagation where needed
      parametersBundle.getFlags().setTrueReplace(true);
      parametersBundle.setDerivationType(derivationType);
      if (DerivationType.DEPTH_SPECIFIED.name().equals(derivationType) && derivationDepth != null) {
        parametersBundle.setDerivationDepth(derivationDepth);
      }
      Map<String, String> setupKnowledgeValues =
          setupKnowledgeExtractor.processProtocolModel(originalRules);
      parametersBundle.setExistingSetupKnowledge(setupKnowledgeValues);
      parametersBundle =
          ForgetMutationParser.parseForgetMutations(
              originalRules, parametersBundle, fileContent);
      parametersBundle.getFlags().setForgetMutation(true);

      parametersBundle.getCollections().clear();
      parametersBundle.setFileName(file.getOriginalFilename());
      parametersBundle.setVariantsTruncated(false);

      mutationGeneratorService.generateMutation(originalRules, mutationSet, parametersBundle);

      if (parametersBundle.getDefeasibleLogicMode()) {
        return zipService.createDlZipResponse(parametersBundle);
      }

      // Stop capturing and get derivation tree content
      derivationTreeContent = derivationTreeCaptureService.stopCaptureAndGet();
      if (derivationTreeContent != null) {
        log.info("Captured derivation tree output ({} characters)", derivationTreeContent.length());
      }

      // Extract base filename for ZIP creation
      String originalFilename = file.getOriginalFilename();
      String baseFileName = originalFilename.split("\\.(?=[^\\.]+$)")[0];
      ResponseEntity<?> response = zipService.createZipResponse(baseFileName, derivationTreeContent);
      if (parametersBundle.isVariantsTruncated()) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        headers.add("X-Variants-Truncated", "true");
        return ResponseEntity.status(response.getStatusCode()).headers(headers).body(response.getBody());
      }
      return response;
    } catch (IllegalArgumentException e) {
      log.error("Error generating forget mutations: " + e.getMessage(), e);
      return ResponseEntity.status(400).body(e.getMessage());
    } catch (Exception e) {
      log.error("Error generating forget mutations: " + e.getMessage(), e);
      return ResponseEntity.status(500).body(e.getMessage());
    } finally {
      // Restore the static derivation caps so the next request starts fresh
      // even if this one tripped them (e.g. INFINITE mode that caused OOM).
      if (parametersBundle != null && !Boolean.TRUE.equals(parametersBundle.getDefeasibleLogicMode())) forgetDerivationChecker.resetLimits();
      if (haskellWasEnabled) {
        com.xmen.service.impl.HybridDerivationService.disableHaskellDerivation();
        DerivationModeContext.disableHaskell();
        log.info("Haskell derivation DISABLED after forget mutation processing");
      }
      // Clear any remaining capture state
      derivationTreeCaptureService.clearCapture();
    }
  }

  /** Source-compatible entry point for clients predating the optional semantics header. */
  public ResponseEntity<?> forgetMutations(MultipartFile file, Boolean haskellActivate,
      String derivationType, Integer derivationDepth, Integer maxVariantsPerRule,
      String blockingModeHeader, String witnessActionsHeader) throws Exception {
    return forgetMutations(file, haskellActivate, derivationType, derivationDepth,
        maxVariantsPerRule, blockingModeHeader, witnessActionsHeader, null);
  }

  private static void validateDlSource(String source) {
    String declarations = source.replaceAll("(?s)/\\*.*?\\*/|//[^\\r\\n]*", " ");
    if (Pattern.compile("\\[\\s*private\\s*\\]|\\bequations\\s*:").matcher(declarations).find()) {
      throw new IllegalArgumentException("Forget-DL does not support private functions or user-defined equations");
    }
    var builtins = Pattern.compile("\\bbuiltins\\s*:\\s*([A-Za-z-]+(?:\\s*,\\s*[A-Za-z-]+)*)").matcher(declarations);
    while (builtins.find()) {
      for (String name : builtins.group(1).split(",")) {
        if (!Set.of("symmetric-encryption", "asymmetric-encryption", "hashing", "signing").contains(name.trim())) {
          throw new IllegalArgumentException("Forget-DL unsupported builtin: " + name.trim());
        }
      }
    }
  }

  private static void validateDlRules(String source) {
    var listener = new org.antlr.v4.runtime.BaseErrorListener() {
      @Override public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer,
          Object token, int line, int position, String message,
          org.antlr.v4.runtime.RecognitionException cause) {
        throw new IllegalArgumentException("Forget-DL input syntax error at " + line + ":" + position + ": " + message);
      }
    };
    var lexer = new TamarinLexer(org.antlr.v4.runtime.CharStreams.fromString(source));
    lexer.removeErrorListeners();
    lexer.addErrorListener(listener);
    var parser = new TamarinParser(new org.antlr.v4.runtime.CommonTokenStream(lexer));
    parser.removeErrorListeners();
    parser.addErrorListener(listener);
    parser.theory();
    if (parser.getCurrentToken().getType() != org.antlr.v4.runtime.Token.EOF) {
      throw new IllegalArgumentException("Forget-DL input has unparsed trailing content");
    }
  }

  private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\w+");

  private static BlockingMode parseBlockingMode(String raw) {
    if (raw == null) {
      return null;
    }
    String normalized = raw.trim().toUpperCase(Locale.ROOT);
    switch (normalized) {
      case "1":
      case "CASE1":
      case "CASE1_WEAK":
      case "WEAK":
        return BlockingMode.CASE1_WEAK;
      case "2":
      case "CASE2":
      case "CASE2_PAIRING":
      case "PAIRING":
        return BlockingMode.CASE2_PAIRING;
      case "3":
      case "CASE3":
      case "CASE3_FULL_DY":
      case "FULL_DY":
      case "FULL":
        return BlockingMode.CASE3_FULL_DY;
      default:
        throw new IllegalArgumentException("Unknown Blocking-Mode: " + raw);
    }
  }
}
