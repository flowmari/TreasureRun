# Coverage Visibility

This page documents the current coverage-reporting path for the root TreasureRun plugin module.

The goal is visibility first: make the local and CI coverage report easy to generate and inspect before introducing any coverage gate.

## Local command

    ./gradlew clean test jacocoTestReport

## Report output

After the command finishes, open:

    build/reports/jacoco/test/html/index.html

The XML report is generated at:

    build/reports/jacoco/test/jacocoTestReport.xml

## Current scope

- Covers the root Spigot plugin module test task.
- The default test task continues to exclude Docker-backed integration tests tagged with `integration`.
- No coverage gate is enforced in this PR.
- Coverage gates can be considered later, after the generated report has been reviewed.

## CI behavior

The main CI workflow generates the JaCoCo HTML report and uploads it as a workflow artifact.

## Non-goals

This document and the related build configuration do not change runtime behavior.

They do not change:

- Java implementation
- database schema
- ranking API behavior
- runtime localization behavior
- language files
- ResourcePack assets
- Fabric behavior
