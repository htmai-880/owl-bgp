# OWL-BGP

**Note:** See the original repository [here](https://github.com/iliannakollia/owl-bgp). OWL-BGP is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

OWL-BGP is a SPARQL query engine built on [Apache Jena ARQ](https://jena.apache.org/) in which
**basic graph patterns (BGPs) are evaluated under the OWL 2 Direct Semantics**, i.e. it implements the
SPARQL OWL Direct Semantics entailment regime. Query answers therefore include inferred knowledge
(e.g. subclasses, subproperties, class membership through restrictions), not only what is stated
in the ontology.

Any OWL API 5 `OWLReasoner` can be used as the back end. The default is
[HermiT](http://www.hermit-reasoner.com), for which a cost-based ordering of the query atoms
is available.

- [Requirements and building](#requirements-and-building)
- [Quick start](#quick-start)
- [Loading ontologies](#loading-ontologies)
- [Writing queries](#writing-queries)
- [Query atom ordering](#query-atom-ordering)
- [Monitoring and timing](#monitoring-and-timing)
- [Command-line tool (QueryCLI)](#command-line-tool-querycli)
- [Interactive console (GUI_Demo)](#interactive-console-gui_demo)
- [Utilities](#utilities)
- [Limitations and troubleshooting](#limitations-and-troubleshooting)
- [Sample ontologies](#sample-ontologies)
- [Licence and third-party libraries](#licence-and-third-party-libraries)

## Requirements and building

- JDK 21 or newer (a JRE alone is not enough to build)
- Maven 3

Dependencies: OWL API 5.1.9, HermiT 1.4.5.519 (`net.sourceforge.owlapi`), Jena/ARQ 2.11.0,
log4j 1.2.17, slf4j 1.7.36 (see [pom.xml](pom.xml)).

```sh
mvn -B package                # compile, run the tests, build the jar
mvn -B package -DskipTests    # build without running the tests
mvn -B test                   # run the tests only
```

`mvn package` creates a self-contained (shaded) jar, `target/owl-bgp-0.1.0-SNAPSHOT.jar`, that
contains OWL-BGP and all its dependencies. The Ant file [build.xml](build.xml) simply delegates to Maven.

To use OWL-BGP from your own project, either put that jar on the classpath or run
`mvn install` and depend on the artifact declared in [pom.xml](pom.xml).

## Quick start

The engine has two parts: an `OWLOntologyDataSet`, which wraps the ontology (and its reasoner), and an
`OWLReasonerSPARQLEngine`, which evaluates a SPARQL query over the data set and returns a standard Jena
`ResultSet`.

```java
import java.io.File;

import org.semanticweb.sparql.OWLReasonerSPARQLEngine;
import org.semanticweb.sparql.arq.OWLOntologyDataSet;
import org.semanticweb.sparql.bgpevaluation.monitor.MinimalPrintingMonitor;

import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;

public class PizzaQuery {
    public static void main(String[] args) throws Exception {
        // 1. Load the ontology (args[0] is the path to e.g. pizza.owl)
        OWLOntologyDataSet dataSet = new OWLOntologyDataSet(new File(args[0]));

        // 2. Create the engine (the monitor is optional)
        OWLReasonerSPARQLEngine engine = new OWLReasonerSPARQLEngine(new MinimalPrintingMonitor());

        // 3. Query
        String prefixes =
            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
            "PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
            "PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>\n";

        // All (inferred) subclasses of CheeseTopping
        String q1 = prefixes + "SELECT ?x WHERE { ?x rdfs:subClassOf pizza:CheeseTopping . }";
        ResultSetFormatter.out(System.out, engine.execQuery(q1, dataSet));

        // Which fillers of hasTopping does American have, restricted to meat toppings?
        String q2 = prefixes +
            "SELECT ?topping WHERE {\n" +
            "  pizza:American rdfs:subClassOf [ a owl:Restriction ;\n" +
            "                                   owl:onProperty pizza:hasTopping ;\n" +
            "                                   owl:someValuesFrom ?topping ] .\n" +
            "  ?topping rdfs:subClassOf pizza:MeatTopping .\n" +
            "}";
        ResultSet results = engine.execQuery(q2, dataSet);
        ResultSetFormatter.out(System.out, results);
    }
}
```

Compile and run it against the jar built above (the pizza ontology ships with the tests):

```sh
J=target/owl-bgp-0.1.0-SNAPSHOT.jar
javac --release 21 -cp $J PizzaQuery.java
java -Dordering=Dynamic -cp $J:. PizzaQuery \
     test/org/semanticweb/sparql/owlbgp/evaluation/ontologies/pizza.owl
```

Output (abridged):

```
BGP Evaluation finished in: 67 ms with 9 results.
| x                                                                          |
| <http://www.co-ode.org/ontologies/pizza/pizza.owl#GorgonzolaTopping>       |
| <http://www.co-ode.org/ontologies/pizza/pizza.owl#GoatsCheeseTopping>      |
| ...                                                                        |
| <http://www.w3.org/2002/07/owl#Nothing>                                    |
BGP Evaluation finished in: 77 ms with 2 results.
| topping                                                                   |
| <http://www.co-ode.org/ontologies/pizza/pizza.owl#MeatTopping>            |
| <http://www.co-ode.org/ontologies/pizza/pizza.owl#PeperoniSausageTopping> |
```

(The "BGP Evaluation finished" lines are printed by `MinimalPrintingMonitor`. The first query also
returns `owl:Nothing`, because it is a subclass of everything, and `MeatTopping` itself, because
`rdfs:subClassOf` is reflexive.)

`ResultSet` is the regular Jena API, so everything Jena offers works, e.g.:

```java
while (results.hasNext()) {
    QuerySolution row = results.nextSolution();
    System.out.println(row.get("topping"));   // RDFNode; row.getResource("topping").getURI() for the IRI
}
```

## Loading ontologies

All constructors of `org.semanticweb.sparql.arq.OWLOntologyDataSet`:

| Constructor | Meaning |
|---|---|
| `OWLOntologyDataSet(File ontology)` | default graph from a local file |
| `OWLOntologyDataSet(File ontology, List<File> namedGraphs)` | additionally registers named graphs |
| `OWLOntologyDataSet(String uri)` | default graph from a URI |
| `OWLOntologyDataSet(String uri, List<String> namedGraphUris)` | URI variant with named graphs |
| `OWLOntologyDataSet(List<String> graphURIs, List<String> namedGraphURIs)` | several default graph URIs, merged |
| `OWLOntologyDataSet(OWLOntology ontology, Map<String,OWLOntology> namedGraphs)` | use ontologies you already loaded with the OWL API |

If you already work with the OWL API, pass your own ontology (imports are respected):

```java
OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new File("my.owl"));
OWLOntologyDataSet dataSet = new OWLOntologyDataSet(ontology, new HashMap<String, OWLOntology>());
```

Queries can also name their source with `FROM <uri>`; the referenced documents are loaded and merged into
the default graph for that query.

### Pre-classification and direct reasoner access

The reasoner of the default graph can be accessed, e.g. to classify the ontology up front so that this cost
is not counted in the first query:

```java
OWLReasoner reasoner = dataSet.getDefaultGraph().getReasoner();
reasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY,
                              InferenceType.OBJECT_PROPERTY_HIERARCHY,
                              InferenceType.DATA_PROPERTY_HIERARCHY);
```

The default reasoner is `org.semanticweb.sparql.HermiT.OWLBGPHermiT`, which exposes the HermiT statistics used
for the cost-based atom ordering.

## Writing queries

Queries are standard SPARQL 1.1 `SELECT` queries. Triple patterns in a BGP are interpreted as OWL
axioms/assertions (an RDF-to-OWL mapping in the style of the OWL 2 RDF mapping) and are answered by the
reasoner under the Direct Semantics, so answers are inferred, not just looked up.

Things that work (all are exercised by the tests in [test](test)):

- **Instance queries**: `?x rdf:type :Student`, `?x :takesCourse ?c`. Answers are named individuals.
- **Class and property variables**: `?x rdf:type ?c`, `?c rdfs:subClassOf :Person`, `?x ?p :Course1`.
  Variables in class position range over *named classes*.
- **Complex class expressions** through the RDF mapping, with blank nodes, e.g. an existential restriction
  `[ a owl:Restriction ; owl:onProperty :hasTopping ; owl:someValuesFrom ?o ]`.
- **Blank nodes in queries** act as non-distinguished variables (existentially quantified,
  they are not returned).
- **Other SPARQL 1.1 algebra** evaluated by ARQ on top of the BGP answers: `FILTER`, `OPTIONAL`, `UNION`,
  sub-queries with `GROUP BY` / aggregates, `MINUS`, `NOT EXISTS`, `LIMIT`/`OFFSET`/`ORDER BY`, `DISTINCT`.
- **Punning**: an IRI may be used as a class and as an individual. Where the role of an IRI is ambiguous the
  surrounding triples are used to disambiguate it.

Example with the LUBM `univ-bench` vocabulary (the LUBM data files import the ontology from the web, so
make sure the import is resolvable, or load them from a copy that does not import it):

```sparql
PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
PREFIX ub:  <http://www.lehigh.edu/~zhp2/2004/0401/univ-bench.owl#>

SELECT ?x ?course WHERE {
  ?x rdf:type ub:Student .          # also returns GraduateStudent, UndergraduateStudent, ...
  ?x ub:takesCourse ?course .
}
```

Remember:

- Entities used in a query must be **declared** in the ontology (or its imports), otherwise their type
  cannot be determined.
- Always declare the prefixes you use. The console additionally predefines some (see below).
- Only `SELECT` is supported by `execQuery`.

## Query atom ordering

The order in which the atoms of a BGP are evaluated has a large influence on run time. Select the strategy
with the system property `ordering`:

| Value | Behaviour |
|---|---|
| `Static` (default) | cost estimate computed once, before evaluation, from HermiT statistics |
| `Dynamic` | re-estimates the cost of the remaining atoms after each atom has been evaluated |
| `PlanChecking`, `Intersection` | alternative strategies used for evaluation experiments |

```sh
java -Dordering=Dynamic -cp target/owl-bgp-0.1.0-SNAPSHOT.jar:. PizzaQuery pizza.owl
```

The property is read each time a BGP is evaluated, so it can also be set programmatically with
`System.setProperty("ordering", "Dynamic")`.

## Monitoring and timing

Pass a `Monitor` to the engine constructor to observe the evaluation (package
`org.semanticweb.sparql.bgpevaluation.monitor`):

| Class | Output |
|---|---|
| `MinimalPrintingMonitor` | one line per BGP: run time and number of results (optionally takes a `PrintStream`) |
| `PrintingMonitor` | detailed trace of the evaluation (optionally takes a `PrintStream`) |
| `TimingMonitor` | base class that records timings without printing (extended by the printing monitors) |
| `MonitorAdapter` | empty implementation; extend it to implement your own monitor |

```java
new OWLReasonerSPARQLEngine(new PrintingMonitor(System.err));
new OWLReasonerSPARQLEngine();                // no monitor
```

The evaluation time of the last BGP is also stored in the Jena context under the symbol
`OWLReasonerSPARQLEngine.BGP_EXEC_TIME` (`ARQ.getContext().get(OWLReasonerSPARQLEngine.BGP_EXEC_TIME)`).

## Command-line tool (QueryCLI)

`org.semanticweb.sparql.QueryCLI` takes an ontology and a query and prints the results to standard output
(diagnostics and timing go to standard error, so the output can be piped):

```
QueryCLI [-v] [-f text|csv|tsv|json|xml] <ontology file or URI> (<query string> | -q <query file> | -)
```

| Argument | Meaning |
|---|---|
| `<ontology>` | local file or URI of the ontology |
| `<query string>` | the SPARQL query itself |
| `-q <file>` | read the query from a file |
| `-` | read the query from standard input |
| `-f <format>` | output format: `text` (default table), `csv`, `tsv`, `json`, `xml` |
| `-v` | print evaluation time and result count to stderr |

```sh
J=target/owl-bgp-0.1.0-SNAPSHOT.jar
O=test/org/semanticweb/sparql/owlbgp/evaluation/ontologies/pizza.owl

# query as argument, CSV output
java -cp $J org.semanticweb.sparql.QueryCLI -f csv $O \
  'PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
   PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>
   SELECT ?x WHERE { ?x rdfs:subClassOf pizza:CheeseTopping }'

# query from a file, with timing and dynamic ordering
java -Dordering=Dynamic -cp $J org.semanticweb.sparql.QueryCLI -v $O -q query.rq

# query from stdin
cat query.rq | java -cp $J org.semanticweb.sparql.QueryCLI -f tsv $O -
```

Exit status is 2 on wrong arguments. A query that fails to parse prints the `ParseException` described under
[troubleshooting](#limitations-and-troubleshooting) to stderr and produces an empty result.

## Interactive console (GUI_Demo)

`org.semanticweb.sparql.GUI_Demo` is a small Swing application (a display is required) to try out queries:

```sh
java -Dordering=Dynamic -jar target/owl-bgp-0.1.0-SNAPSHOT.jar path/to/ontology.owl
```

Type or paste a query and enter `GO` to run it. The following prefixes are predefined:
`rdf`, `rdfs`, `owl`, `xsd`, `swrl`, `swrlb`, `swrlx`, `ruleml`, `ex:` (`http://example.org/test#`) and
`ub:` (`http://www.lehigh.edu/~zhp2/2004/0401/univ-bench.owl#`).

## Utilities

`org.semanticweb.sparql.bgpevaluation.Skolemizer` replaces the blank nodes of an ontology by named individuals:

```sh
java -cp target/owl-bgp-0.1.0-SNAPSHOT.jar \
     org.semanticweb.sparql.bgpevaluation.Skolemizer file.owl      # writes file-anonymised.owl
```

## Limitations and troubleshooting

- Only `SELECT` queries are supported through `execQuery`.
- Blank nodes in the *data* are skolemized, so they cannot be returned as such.
- With OWL API 5 plain literals are `xsd:string` and show up as `"text"^^xsd:string` in results.
- If a query cannot be parsed into OWL axioms, or the type of an entity cannot be disambiguated, the engine
  prints `ParseException: Probably types could not be disambuguated with this active graph` followed by a
  stack trace to `stderr` and returns an **empty result**. Typical causes: an entity that is not declared in the
  ontology, or a typo in an IRI/prefix. Check your prefixes and declarations first.
- `UnloadableImportException` while loading: the ontology imports a document that cannot be fetched
  (e.g. the LUBM data files import `univ-bench.owl` from the web). Provide the import locally, e.g. via an
  OWL API IRI mapper, or use the `OWLOntology` constructor with an ontology you loaded yourself.
- Setting `ARQ.optimization` is a global Jena setting and affects other ARQ users in the same JVM.
- Startup of HermiT classification dominates the first query on large ontologies; see
  [pre-classification](#pre-classification-and-direct-reasoner-access).

## Sample ontologies

| Location | Content |
|---|---|
| `test/org/semanticweb/sparql/owlbgp/evaluation/ontologies/` | `pizza.owl`, `Galen.owl`, `GalenExplanation.owl` (used by the tests) |
| `evaluation/ontologies/` | `univ-bench.owl` and the LUBM data sets `LUBM-1`, `LUBM-2`, `LUBM-5` |
| `evaluation/ontologies2/` | LUBM-DL and UOBM variants |
| `evaluation/newOntologies/` | `Galen.owl`, `FBbt_XP.owl` |

The tests in [test](test) (`QueryTests`, `TestPizzaQueries`, ...) are the best collection of example
queries.

## Licence and third-party libraries

OWL-BGP is free software released under the GNU Lesser General Public License, version 3 or
(at your option) any later version. See [lgpl-3.0.txt](lgpl-3.0.txt) and [gpl.txt](gpl.txt). The full list of
third-party libraries and their licences is in [readme.txt](readme.txt) and in [lib](lib).

More information: <http://code.google.com/p/owl-bgp/>, or contact Birte Glimm.
