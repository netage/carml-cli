package info.resc.rml.carml.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.IOUtils;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.impl.LinkedHashModel;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParserRegistry;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.eclipse.rdf4j.rio.Rio;

import com.taxonic.carml.engine.RmlMapper;
import com.taxonic.carml.logical_source_resolver.XPathResolver;
import com.taxonic.carml.model.TriplesMap;
import com.taxonic.carml.util.RmlMappingLoader;
import com.taxonic.carml.vocab.Rdf;

public class Main 
{
	static CommandLine commandLine;
	public static String outputFile = "";
	public static String mappingFile = "";
	public static String inputFile = "";
	public static String inputFolder = "";
	public static String outputFormat = "";

	public static void main( String[] args ) throws Exception
	{
		CommandLineParser cliParser = new DefaultParser();
		commandLine = cliParser.parse(generateCLIOptions(), args);

		if (commandLine.hasOption("h")) {
			Main.displayHelp();
		}

		if (commandLine.hasOption("o")) {
			Main.outputFile = commandLine.getOptionValue("o", "output.ttl");
		}

		if (commandLine.hasOption("m")) {
			mappingFile = commandLine.getOptionValue("m", "");
		}

		if (commandLine.hasOption("i")) {
			Main.inputFile = commandLine.getOptionValue("i", "");
		}

		if (commandLine.hasOption("f")) {
			Main.inputFolder = commandLine.getOptionValue("f", "");
		}

		if (commandLine.hasOption("of")) {
			Main.outputFormat = commandLine.getOptionValue("of", "nt");
		}

		Model result = new LinkedHashModel();
		System.out.println("Start converting...");
		if(Main.inputFile.isEmpty()){
			if(!Main.inputFolder.isEmpty()){
				System.out.println("Convert folder: "+Main.inputFolder);
				result.addAll(convertFolder());
			}else{
				result.addAll(convertFile(null, false));
			}
		}else{
			System.out.println("Convert file: "+Main.inputFile);
			result.addAll(convertFile(new FileInputStream(Main.inputFile), true));
		}

		System.out.println("Done converting, print model to file.");
		try {
			printModel2File(result);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Conversion Completed!");
	}

	private static Model convertFolder() {
		Model result = new LinkedHashModel();
		File dir = new File(Main.inputFolder);
		File[] directoryListing = dir.listFiles();

		if (directoryListing != null) {
			for(int i=0;i<directoryListing.length;i++){
				System.out.println("Convert file: " + directoryListing[i].getPath());
				try {
					result.addAll(convertFile(new FileInputStream(directoryListing[i].getAbsolutePath()), true));
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		return result;
	}

	private static RDFFormat determineRdfFormat() {
		if(Main.outputFormat.toLowerCase().equals("ttl"))
			return RDFFormat.TURTLE;
		else if(Main.outputFormat.toLowerCase().equals("nt"))
			return RDFFormat.NTRIPLES;
		else if(Main.outputFormat.toLowerCase().equals("nq"))
			return RDFFormat.NQUADS;
		else if(Main.outputFormat.toLowerCase().equals("rdf"))
			return RDFFormat.RDFXML;
		else if(Main.outputFormat.toLowerCase().equals("jsonld"))
			return RDFFormat.JSONLD;
		else if(Main.outputFormat.toLowerCase().equals("trig"))
			return RDFFormat.TRIG;
		else if(Main.outputFormat.toLowerCase().equals("n3"))
			return RDFFormat.N3;
		else if(Main.outputFormat.toLowerCase().equals("trix"))
			return RDFFormat.TRIX;
		else if(Main.outputFormat.toLowerCase().equals("brf"))
			return RDFFormat.BINARY;
		else if(Main.outputFormat.toLowerCase().equals("rj"))
			return RDFFormat.RDFJSON;
		else
			return RDFFormat.TURTLE;
	}

	public static void printModel2File(Model model) throws IOException{	
		StringWriter outString = new StringWriter();

		Rio.write(model, outString, determineRdfFormat());

		File file = new File(Main.outputFile);
		file.createNewFile();


		FileWriter fileWriter = new FileWriter(file, false);
		fileWriter.write(outString.toString());
		fileWriter.flush();
		fileWriter.close();
	}

	private static Model convertFile(InputStream inputStream, boolean useStream){

		Set<TriplesMap> mapping =
				RmlMappingLoader
				.build()
				.load(Paths.get(Main.mappingFile), RDFFormat.TURTLE);

		RmlMapper mapper = RmlMapper.newBuilder()
				.setLogicalSourceResolver(Rdf.Ql.XPath, new XPathResolver())
				.build();

		if(useStream){
			mapper.bindInputStream(inputStream);
		}

		return mapper.map(mapping);
	}

	private static Options generateCLIOptions() {

		Options cliOptions = new Options();

		cliOptions.addOption("h", "help", false, 
				"show this help message");
		cliOptions.addOption("m", "mapping document", true, 
				"the URI of the mapping file (required)");
		cliOptions.addOption("o", "output file", true, 
				"the URI of the output file (required)");
		cliOptions.addOption("i", "input format", true, 
				"the URI of the input file (optional)");
		cliOptions.addOption("f", "input folder", true, 
				"the URI of a folder with input files (optional)");
		cliOptions.addOption("of", "output format", true, 
				"The rdf format used for the output (optional)");
		return cliOptions;
	}

	public static void displayHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("Caramel CLI interface", generateCLIOptions());
		System.exit(1);
	}
}
