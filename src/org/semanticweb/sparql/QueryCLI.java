package org.semanticweb.sparql;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.semanticweb.sparql.arq.OWLOntologyDataSet;
import org.semanticweb.sparql.bgpevaluation.monitor.MinimalPrintingMonitor;

import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.query.ResultSetFormatter;

/**
 * Command line tool: evaluates a SPARQL SELECT query over an ontology with OWL 2 Direct Semantics
 * and prints the results to standard output.
 */
public class QueryCLI {

    private static void usage() {
        System.err.println("Usage: QueryCLI [-v] [-f text|csv|tsv|json|xml] <ontology file or URI> (<query string> | -q <query file> | -)");
        System.err.println("  '-' reads the query from standard input; -v prints evaluation timing to stderr.");
        System.err.println("  Use -Dordering=Static|Dynamic to select the atom ordering.");
    }

    public static void main(String[] args) throws Exception {
        boolean verbose=false;
        String format="text";
        String ontology=null;
        String query=null;
        for (int i=0;i<args.length;i++) {
            String a=args[i];
            if ("-v".equals(a))
                verbose=true;
            else if ("-f".equals(a) && i+1<args.length)
                format=args[++i];
            else if ("-q".equals(a) && i+1<args.length)
                query=new String(Files.readAllBytes(new File(args[++i]).toPath()), StandardCharsets.UTF_8);
            else if ("-".equals(a))
                query=new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
            else if (ontology==null)
                ontology=a;
            else if (query==null)
                query=a;
            else {
                usage();
                System.exit(2);
            }
        }
        if (ontology==null || query==null) {
            usage();
            System.exit(2);
        }
        OWLOntologyDataSet dataSet;
        if (ontology.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*") && !new File(ontology).exists())
            dataSet=new OWLOntologyDataSet(ontology);
        else
            dataSet=new OWLOntologyDataSet(new File(ontology));
        OWLReasonerSPARQLEngine engine=verbose ? new OWLReasonerSPARQLEngine(new MinimalPrintingMonitor(System.err)) : new OWLReasonerSPARQLEngine();
        ResultSet results=engine.execQuery(query, dataSet);
        switch (format) {
            case "csv": ResultSetFormatter.outputAsCSV(System.out, results); break;
            case "tsv": ResultSetFormatter.outputAsTSV(System.out, results); break;
            case "json": ResultSetFormatter.outputAsJSON(System.out, results); break;
            case "xml": ResultSetFormatter.outputAsXML(System.out, results); break;
            default: ResultSetFormatter.out(System.out, results);
        }
        System.out.flush();
    }
}
