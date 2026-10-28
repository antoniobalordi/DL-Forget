# Forget-DL

Source code, tests, manuscript and reproduction artifact for *Reasoning about
Memory Lapses in Security Ceremonies with Defeasible Logic*.

This repository contains one Java implementation. The paper and experiment
harness use that implementation directly.

## Where to start

| Directory | Contents |
|---|---|
| [X-Men_2.0/](X-Men_2.0/) | Java application, Maven wrapper and tests |
| [paper/](paper/) | LaTeX manuscript; start with `main.tex` |
| [artifact/](artifact/) | Reproduction script, harness, reference encoding and saved evidence |

The DL implementation is in
[X-Men_2.0/src/main/java/com/xmen/service/forget/dl/](X-Men_2.0/src/main/java/com/xmen/service/forget/dl/).
`AmbiguityBlockingReasoner` implements DL inference, `ForgetDlCompiler` compiles
memory states into theories, and `ForgetDlEngine` makes forgetting decisions.
The [semantic contract](X-Men_2.0/docs/forget-dl/SEMANTIC_CONTRACT.md) records
the supported models and the interpretation of the formal definitions.

The **110 core tests** are in
[X-Men_2.0/src/test/java/com/xmen/service/forget/dl/](X-Men_2.0/src/test/java/com/xmen/service/forget/dl/),
in ten test classes. Host integration tests are in the adjacent `forget/`
package and `com/xmen/integrationTests/`.

## Run the tests

Use JDK 21. From this repository's root:

```sh
cd X-Men_2.0
./mvnw -B test '-Dtest=com.xmen.service.forget.dl.*Test'
./mvnw -B test '-Dtest=ForgetDlHostIntegrationTest,ForgetNeglectTriggerTest,ForgetMutation*'
./mvnw -B test '-Dtest=*Test,*Tests,!XMenInterfaceTest,!DisplayScaleSupportTest'
./mvnw -B test '-Dtest=XMenInterfaceTest,DisplayScaleSupportTest'
```

Expected core result: 110 tests. Previously recorded application results: 41 host/mutation
tests; 221 reported in the broader suite (219 pass, two intentionally disabled
lexer/parser diagnostics); 13 GUI tests. The groups overlap. Run the GUI group
separately in a desktop session. Maven downloads dependencies on its first run.

## Reproduce the paper's experiments

Use JDK 21 and Python 3. From this repository's root:

```sh
python3 -m venv .venv
. .venv/bin/activate
python3 -m pip install -r artifact/requirements.txt
./artifact/reproduce.sh
```

The script locates `X-Men_2.0/` in this repository, compiles the same DL core
and its 110 tests with `javac`, runs the differential ASP oracle and the research
studies, and runs Tamarin when Tamarin and Maude are on `PATH`.
The `javac` route uses the small JUnit API shim in `artifact/shim/`; Maven
uses JUnit itself and also exercises the application integration.

The reference ASP files in `artifact/vendor/Defeasible-Deontic-Logic/` are
pinned to commit `679c638c2fddf97e1ee624cc528e84be6fd02d95`. They are an
independent oracle, not a second Java implementation. See their
[provenance](artifact/vendor/PROVENANCE.md).

Fresh results go to **`artifact/generated/`**, which is ignored by Git.
Recorded outputs in `artifact/results/` and models/logs in `artifact/tamarin/`
remain available for comparison. Timings depend on the machine. Tamarin
reproduction uses Tamarin 1.12.0 and Maude 3.5.1; without them the script
explicitly reports that this stage was skipped.

The manuscript's scalability table retains measurements from before the usability
fallback. Fresh runs use saturation to resolve eligible truncated target searches;
the exponential families now yield Preserve/Skip rather than Unknown.

To verify the supplied model checksums:

```sh
cd artifact/tamarin
shasum -a 256 -c SHA256SUMS
```

## Build the manuscript

With a LaTeX installation containing the packages used by the manuscript:

```sh
cd paper
latexmk -pdf main.tex
```

The LNCS class and bibliography style are included. Generated LaTeX files,
Python environments and Java build outputs are excluded from the source package.

## Run the application

See [X-Men_2.0/README.md](X-Men_2.0/README.md). The application retains its
existing license at [X-Men_2.0/LICENSE](X-Men_2.0/LICENSE).
