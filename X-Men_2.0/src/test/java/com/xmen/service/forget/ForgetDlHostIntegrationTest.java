package com.xmen.service.forget;

import com.xmen.service.impl.ForgetMutationStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@SpringBootTest
@ActiveProfiles("test")
class ForgetDlHostIntegrationTest {
  @Autowired WebApplicationContext application;
  @Autowired ForgetMutationStrategy strategy;

  private MvcResult request(String source, String witnesses, Integer limit) throws Exception {
    MockMvc mvc = MockMvcBuilders.webAppContextSetup(application).build();
    var request = multipart("/api/forget/mutations").file(new MockMultipartFile("file", "DlHost.spthy", "text/plain", source.getBytes(StandardCharsets.UTF_8)))
        .header("Forget-Semantics", "DL");
    if (witnesses != null) request.header("Witness-Actions", witnesses);
    if (limit != null) request.header("Max-Variants-Per-Rule", limit);
    return mvc.perform(request).andReturn();
  }

  private Map<String, String> archive(MvcResult response) throws Exception {
    assertThat(response.getResponse().getStatus()).as(response.getResponse().getContentAsString()).isEqualTo(200);
    Map<String, String> entries = new LinkedHashMap<>();
    try (var zip = new ZipInputStream(new ByteArrayInputStream(response.getResponse().getContentAsByteArray()))) {
      for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
        entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
      }
    }
    return entries;
  }

  private String model(Map<String, String> archive) {
    return archive.entrySet().stream().filter(e -> e.getKey().endsWith(".m")).findFirst().orElseThrow().getValue();
  }

  private String theory(String rules) {
    return "theory DlHost\nbegin\nbuiltins: symmetric-encryption\n/****RULES****/\n" + rules + "\n/****ENDOFRULES****/\nend\n";
  }

  private String passwords(String agent) {
    return "rule humansetup: [Fr(~pw1),Fr(~pw2),Fr(~pw3)] --[OnlyOnce()]-> [!Type(" + agent + ",'password',~pw1),!Type(" + agent + ",'password',~pw2),!Type(" + agent + ",'password',~pw3)]\n";
  }

  @Test void bankUsesCertifiedReplacementAndKeepsWitnessTrue() throws Exception {
    var result = request(Files.readString(Path.of("src/test/resources/Forget_Bank_Input.spthy")), "PasswordAttempt,U_LoginRequest,LoginSuccess,LoginFailed", null);
    var entries = archive(result);
    String model = model(entries);
    assertThat(model).contains("rule H_1:", "Out(aenc(<$Human,$B1,nH>,pkB1))", "rule H_2_M:", "Send($Human,'m3',senc(<$Human,pw2>,k(nH,nB)))", "PasswordAttempt($Human,$B1,pw2,nH,nB)");
    assertThat(model).doesNotContain("Forget(");
    assertThat(entries.keySet()).anyMatch(k -> k.endsWith("_CognitiveTrace.txt"));
    assertThat(strategy.isDefeasibleLogicMode()).isFalse();
  }

  @Test void laterSendReevaluatesPersistentForgetAndMutantsAccumulate() throws Exception {
    String input = theory(passwords("'H'") + """
        rule H1: [State('H','1',<pw1,pw2>)] --[H(),Forget(pw1),Send('H','first',pw1)]-> [State('H','2',<pw1,pw2>),Out(pw1)]
        rule H2: [State('H','2',<pw1,pw2>)] --[H(),Send('H','second',pw1)]-> [Out(pw1)]
        """);
    String output = model(archive(request(input, null, null)));
    assertThat(output).contains("rule H1_M:", "rule H2_M:", "Send('H','first',pw2)", "Send('H','second',pw2)").doesNotContain("Out(pw1)");
  }

  @Test void ciphertextReceiptDoesNotBypassForgottenDecryptionAndPostStateDoesNotInjectKnowledge() throws Exception {
    String input = theory("""
        rule H1: [State('H','1',k),In(senc(a,k))] --[H(),Receive('H','incoming',a),ForgetRule('sdec'),Send('H','first',a)]-> [State('H','2',<a,k>),Out(a)]
        rule H2: [State('H','2',<a,k>)] --[H(),Send('H','second',a)]-> [Out(a)]
        """);
    String output = model(archive(request(input, null, null)));
    assertThat(output).contains("rule H1_M:", "rule H2_M:", "In(senc(a,k))", "senc(a,k)").doesNotContain("Out(a)", "ForgetRule(");
  }

  @Test void cuePresenceIsLocalAndAssociationPersists() throws Exception {
    String input = theory(passwords("'H'") + """
        rule H1: [State('H','1',<pw1,pw2>)] --[H(),Forget(pw1),Cue('hint'),AssociateCue('hint',pw1),PresentCue('hint'),Send('H','first',pw1)]-> [State('H','2',<pw1,pw2>),Out(pw1)]
        rule H2: [State('H','2',<pw1,pw2>)] --[H(),Send('H','second',pw1)]-> [Out(pw1)]
        """);
    String output = model(archive(request(input, null, null)));
    assertThat(output).contains("Send('H','first',pw1)", "Send('H','second',pw2)").doesNotContain("Cue(", "AssociateCue(", "PresentCue(");
  }

  @Test void immediateForgottenReceiptNeverEntersLaterState() throws Exception {
    String input = theory("""
        rule H1: [State('H','1','dl_empty'),In(a)] --[H(),Forget(a),Send('H','first',a)]-> [State('H','2',a),Out(a)]
        rule H2: [State('H','2',a)] --[H(),Send('H','second',a)]-> [Out(a)]
        """);
    String output = model(archive(request(input, null, null)));
    assertThat(output).contains("State('H','2','dl_empty')").doesNotContain("Out(a)");
  }

  @Test void freshCurrentValueIsNotInsertedIntoPreviousStateAndIsRebasedNextStep() throws Exception {
    String input = theory("""
        rule H1: [State('H','1',a)] --[H(),ForgetRule('h')]-> [State('H','2',a)]
        rule H2: [State('H','2',a),Fr(~n)] --[H(),Send('H','fresh',~n)]-> [State('H','3',<a,~n>),Out(~n)]
        rule H3: [State('H','3',<a,n>)] --[H(),Send('H','later',n)]-> [Out(n)]
        """);
    String output = model(archive(request(input, null, null)));
    assertThat(output).contains("State('H','2',a)", "Fr(~n)", "Out(~n)", "State('H','3',<a,~n>)", "State('H','3',<a,n>)", "Out(n)");
  }

  @Test void independentVariantsAreCumulativeAndTotalBranchCountIsBounded() throws Exception {
    String input = theory(passwords("'H'") + """
        rule H1: [State('H','1',<pw1,pw2,pw3>)] --[H(),Forget(pw1),Send('H','first',pw1)]-> [State('H','2',<pw1,pw2,pw3>),Out(pw1)]
        rule H2: [State('H','2',<pw1,pw2,pw3>)] --[H(),Send('H','second',pw1)]-> [Out(pw1)]
        """);
    MvcResult response = request(input, null, 2);
    var entries = archive(response);
    assertThat(entries.keySet().stream().filter(k -> k.endsWith(".m")).count()).isEqualTo(2);
    assertThat(response.getResponse().getHeader("X-Variants-Truncated")).isEqualTo("true");
    for (var entry : entries.entrySet()) if (entry.getKey().endsWith(".m")) assertThat(entry.getValue()).contains("rule H1_M", "rule H2_M").doesNotContain("Out(pw1)");
  }

  @Test void directedReplyIsEvaluatedBeforeReceivingHumanAndForwardedWithActualValue() throws Exception {
    String input = theory(passwords("'B'") + """
        rule A1: [State('A','1','dl_empty')] --[H(),Send('A','request','request')]-> [State('A','2','dl_empty'),SndS('A','B','request','request')]
        rule A2: [State('A','2','dl_empty'),RcvS('B','A','reply',pw1)] --[H(),Receive('A','reply',pw1),Send('A','forward',pw1)]-> [Out(pw1)]
        rule B1: [State('B','1',<pw1,pw2>),RcvS('A','B','request','request')] --[H(),Forget(pw1),Send('B','reply',pw1)]-> [State('B','2',<pw1,pw2>),SndS('B','A','reply',pw1)]
        """);
    String output = model(archive(request(input, null, null)));
    assertThat(output).contains("SndS('B','A','reply',pw2)", "RcvS('B','A','reply',pw2)", "Send('A','forward',pw2)", "Out(pw2)");
  }

  @Test void rejectsUnsupportedAndMisspelledInputsInsteadOfEmptySuccess() throws Exception {
    String base = theory("rule H1: [State('H','1',a)] --[H(),ForgetRule('sdee')]-> [Out(a)]");
    MvcResult misspelled = request(base, null, null);
    assertThat(misspelled.getResponse().getStatus()).isEqualTo(400);
    assertThat(misspelled.getResponse().getContentAsString()).contains("Unknown Forget-DL inference rule");
    MvcResult algebra = request(base.replace("builtins: symmetric-encryption", "builtins: hashing,\n xor"), null, null);
    assertThat(algebra.getResponse().getStatus()).isEqualTo(400);
    assertThat(algebra.getResponse().getContentAsString()).contains("unsupported builtin: xor");
  }

  @Test void declaredPublicConstructorRuleCanLapseFromPreamble() throws Exception {
    String input = theory("rule H1: [State('H','1',<a,b>)] --[H(),ForgetRule('key')]-> [Out(key(a,b))]").replace("builtins: symmetric-encryption", "functions: key/2");
    String output = model(archive(request(input, null, null)));
    assertThat(output).contains("rule H1_M:").doesNotContain("Out(key(a,b))");
  }

  @Test void proceduralLapseCannotExecuteInternalCheckFromCiphertextPatternBinding() throws Exception {
    String input = theory("rule H1: [State('H','1',<senc(a,k),k>)] --[H(),ForgetRule('sdec'),Check(a)]-> []");
    MvcResult response = request(input, null, null);
    assertThat(response.getResponse().getStatus()).isEqualTo(400);
    assertThat(response.getResponse().getContentAsString()).contains("cannot certify internal action Check");
  }

  @Test void neglectRemovesOnlyActualOccurrencesAndCertifiesRemainingCheck() throws Exception {
    String input = theory("rule H1: [State('H','1',<p,p1>)] --[H(),Forget(p),Check(p),Other(h(p1))]-> []");
    String output = model(archive(request(input, null, null)));
    assertThat(output).doesNotContain("Check(p)").contains("Other(h(p1))");
  }

  @Test void proceduralLapseCannotExposeBlockedPlaintextThroughCustomOutputFact() throws Exception {
    String input = theory("rule H1: [State('H','1',<senc(a,k),k>)] --[H(),ForgetRule('sdec')]-> [Token(a)]");
    MvcResult response = request(input, null, null);
    assertThat(response.getResponse().getStatus()).isEqualTo(400);
    assertThat(response.getResponse().getContentAsString()).contains("cannot certify output fact Token");
  }

  @Test void neglectCannotKeepSendThatStillNeedsForgottenOperand() throws Exception {
    String input = theory("rule H1: [State('H','1',p)] --[H(),Forget(p),Check(p),Send('H','message',p)]-> [Out(p)]");
    MvcResult response = request(input, null, null);
    assertThat(response.getResponse().getStatus()).isEqualTo(400);
    assertThat(response.getResponse().getContentAsString()).contains("cannot certify surviving send after Neglect");
  }

  @Test void hashingBuiltinRejectsMalformedHashArity() throws Exception {
    String input = theory("rule H1: [State('H','1',<a,b>)] --[H(),ForgetRule('sign')]-> [Out(h(a,b))]").replace("builtins: symmetric-encryption", "builtins: hashing");
    MvcResult response = request(input, null, null);
    assertThat(response.getResponse().getStatus()).isEqualTo(400);
    assertThat(response.getResponse().getContentAsString()).contains("invalid builtin arity: h");
  }

  @Test void replacementPreservesStructuralInequalityConstraint() throws Exception {
    String input = theory(passwords("'H'") + "rule H1: [State('H','1',<pw1,pw2>)] --[H(),Forget(pw1),Neq(pw1,pw2),Send('H','message',pw1)]-> [Out(pw1)]");
    String output = model(archive(request(input, null, null)));
    assertThat(output).contains("Out(pw2)", "Neq(pw1,pw2)").doesNotContain("Neq(pw2,pw2)");
  }

  @Test void invoiceVariantsFromActualHttpZipMatchDeclarativeCueAndProceduralDecisions() throws Exception {
    for (String scenario : new String[]{"NoCue", "Cue", "SignLapse"}) {
      String input = Files.readString(Path.of("src/test/resources/Forget_DL_Invoice_" + scenario + ".spthy"));
      var entries = archive(request(input, null, null));
      String output = model(entries);
      String human = output.substring(output.indexOf("rule H1"), output.indexOf("/****ENDOFRULES****/"));
      if (scenario.equals("NoCue")) assertThat(human).contains("rule H1_M:", "accAtt", "Send('H','order',");
      if (scenario.equals("Cue")) assertThat(human).contains("rule H1:", "Send('H','order',aenc(sign(<'H','B','S','service','amount',accS>,skH),pkB))");
      if (scenario.equals("SignLapse")) assertThat(human).contains("rule H1_M:").doesNotContain("Send('H','order',", "Out(");
      assertThat(output).doesNotContain("Forget(", "ForgetRule(", "AssociateCue(", "PresentCue(");
    }
  }
}
