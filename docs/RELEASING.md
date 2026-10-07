# Releasing

How 0.1.0 was released. Follow the same steps for the next release, replacing the versions. Stop at the first failed check: nothing is tagged unless CI is green on the release commit.

Prerequisites: Java 21, Maven, Docker running (the conformance suite starts Neo4j Enterprise in a Testcontainer), `gh` logged in.

## 1. Clean tree

```bash
git checkout main && git pull --ff-only
git status --short          # must print nothing
```

## 2. Compatibility check

Confirm, and record in the README "Compatibility" table:

- Java release: `maven.compiler.release` in `pom.xml` (21).
- Neo4j version the build targets: `neo4j.version` and `neo4j.image` in `pom.xml` (2026.09.0). Downstream embedders (n20s) must build against the same version.
- SPEC version implemented: the status line of `docs/SPEC.md` (v0.3).
- `rdf-snapshot-neo4j` stays embeddable (DECISIONS D25): no procedure class, Neo4j only in `provided` scope.

```bash
grep -rln "org.neo4j.procedure" rdf-snapshot-neo4j/src/main || echo "no procedure annotations"
mvn -q -B dependency:list -pl rdf-snapshot-neo4j -DoutputFile=/tmp/deps.txt
grep "org.neo4j" /tmp/deps.txt | grep -v ":provided" || echo "Neo4j: provided only"
```

## 3. Release version

```bash
mvn -B versions:set -DnewVersion=0.1.0 -DprocessAllModules -DgenerateBackupPoms=false
git grep -n "SNAPSHOT" | grep -v "SNAPSHOT_ID\|LPG_SNAPSHOT"    # must print nothing
```

The `grep -v` drops code identifiers. Documentation does not name versioned jars (the README uses `rdf-snapshot-procedures-*.jar`), so only the POMs change.

## 4. Full build

```bash
mvn -B install              # unit tests + conformance suite
ls ~/.m2/repository/io/github/halftermeyer/{rdf-snapshot-core,rdf-snapshot-neo4j,rdf-snapshot-procedures}/0.1.0/
mvn -q -B dependency:list -pl rdf-snapshot-procedures -DincludeGroupIds=io.github.halftermeyer -DoutputFile=/dev/stdout
```

Every `io.github.halftermeyer` dependency must resolve to the release version, never to a `-SNAPSHOT` left in `~/.m2`.

## 5. Docs

Update the README "Compatibility" table (library, SPEC, Neo4j, Java) and the JitPack example version.

## 6. Release commit and CI

```bash
git add -A && git commit -m "Release 0.1.0 (SPEC v0.3)"
git push
gh run list --commit "$(git rev-parse HEAD)"          # find the CI run of this commit
gh run watch <run-id> --exit-status                   # must exit 0
```

If CI fails, stop: fix on main and restart from step 4 with a new release commit.

## 7. Tag

```bash
git tag -a v0.1.0 -m "neo4j-rdf-snapshot 0.1.0 (SPEC v0.3)" <release-commit>
git push origin v0.1.0
```

JitPack builds the tag on first request (`jitpack.yml`: JDK 21, `mvn install -DskipTests`): `com.github.halftermeyer.neo4j-rdf-snapshot:<module>:v0.1.0`.

## 8. Next development version

```bash
mvn -B versions:set -DnewVersion=0.2.0-SNAPSHOT -DprocessAllModules -DgenerateBackupPoms=false
git commit -am "Start 0.2.0-SNAPSHOT" && git push
```
