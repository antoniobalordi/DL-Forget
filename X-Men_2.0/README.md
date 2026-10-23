# X-Men with Forget-DL

This is the application module of [Forget-DL](../README.md). It contains the
single Java implementation used by the paper, the research harness and the tests.

Use **JDK 21**. Run from this directory:

```sh
./mvnw spring-boot:run
```

The default server port is 8080. The GUI requires a desktop session.
[ENVIRONMENT_CONFIG.md](ENVIRONMENT_CONFIG.md) describes environment settings;
[HASKELL_DERIVATION_SERVICE.md](HASKELL_DERIVATION_SERVICE.md) documents the
optional Haskell integration. Forget-DL itself is implemented in Java.

## Defeasible Logic

Implementation: `src/main/java/com/xmen/service/forget/dl/`.
Core tests: `src/test/java/com/xmen/service/forget/dl/` (96 tests in eight classes).

Select DL semantics with the HTTP request header `Forget-Semantics: DL`.
The default remains `ALGORITHM1`. See the
[package guide](src/main/java/com/xmen/service/forget/dl/README.md) and
[semantic contract](docs/forget-dl/SEMANTIC_CONTRACT.md).

```sh
./mvnw -B test '-Dtest=com.xmen.service.forget.dl.*Test'
./mvnw -B test '-Dtest=ForgetDlHostIntegrationTest,ForgetNeglectTriggerTest,ForgetMutation*'
./mvnw -B test '-Dtest=*Test,*Tests,!XMenInterfaceTest,!DisplayScaleSupportTest'
./mvnw -B test '-Dtest=XMenInterfaceTest,DisplayScaleSupportTest'
```

Run the GUI tests separately. The project-level [README](../README.md) explains
the conference artifact and its reproduction command.
