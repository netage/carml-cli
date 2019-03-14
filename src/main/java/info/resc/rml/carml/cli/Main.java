package info.resc.rml.carml.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Namespace;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.RDFWriter;
import org.eclipse.rdf4j.rio.RDFWriterRegistry;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings;
import org.eclipse.rdf4j.rio.helpers.JSONLDMode;
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings;
import org.json.JSONObject;

import com.github.jsonldjava.core.JsonLdOptions;
import com.github.jsonldjava.core.JsonLdProcessor;
import com.github.jsonldjava.utils.JsonUtils;
import com.taxonic.carml.engine.RmlMapper;
import com.taxonic.carml.logical_source_resolver.CsvResolver;
import com.taxonic.carml.logical_source_resolver.JsonPathResolver;
import com.taxonic.carml.logical_source_resolver.XPathResolver;
import com.taxonic.carml.model.TriplesMap;
import com.taxonic.carml.util.IoUtils;
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
	public static String mappingFormat = "";

	public static void main( String[] args ) throws Exception
	{
		System.getProperty("file.encoding","UTF-8");
		CommandLineParser cliParser = new DefaultParser();
		commandLine = cliParser.parse(generateCLIOptions(), args);

		if (commandLine.hasOption("h")) {
			Main.displayHelp();
		}

		if (commandLine.hasOption("version")) {
			Main.displayVersion();
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
			Main.outputFormat = commandLine.getOptionValue("of", "ttl");
		}

		if (commandLine.hasOption("mf")) {
			Main.mappingFormat = commandLine.getOptionValue("mf", "ttl");
		}		

		File file = new File(Main.outputFile);
		if(file.exists()){
			file.delete();
		}
		file.createNewFile();

		System.out.println("Start converting...");
		if(Main.inputFile.isEmpty()){
			if(!Main.inputFolder.isEmpty()){
				System.out.println("Convert folder: "+Main.inputFolder);
				convertFolder(file);
			}else{
				convertFile(null, false, file);
			}
		}else{
			System.out.println("Convert file: "+Main.inputFile);
			convertFile(new FileInputStream(Main.inputFile), true, file);
		}

		System.out.println("Conversion Completed!");
	}

	private static void convertFolder(File file) throws Exception {
		File dir = new File(Main.inputFolder);
		File[] directoryListing = dir.listFiles();

		if (directoryListing != null) {
			for(int i=0;i<directoryListing.length;i++){
				System.out.println("Convert file: " + directoryListing[i].getPath());
				try {
					convertFile(new FileInputStream(directoryListing[i].getAbsolutePath()), true, file);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private static RDFFormat determineRdfFormat(String givenFormat) {
		Iterator<RDFFormat> formats = RDFWriterRegistry.getInstance().getKeys().iterator();
		while(formats.hasNext()){
			RDFFormat format = formats.next();
			if(format.hasDefaultFileExtension(givenFormat)){
				return format;
			}
		}
		return RDFFormat.TURTLE;
	}

	public static void printModel2File(Model model, File file) throws IOException{	
		StringWriter outString = new StringWriter();

		if(determineRdfFormat(Main.outputFormat).toString().contains("JSON-LD")){
			StringWriter jsonModel = new StringWriter();
			RDFWriter rdfWriter = Rio.createWriter(RDFFormat.JSONLD, jsonModel);
			//rdfWriter.getWriterConfig().set(BasicWriterSettings.INLINE_BLANK_NODES, true);
			rdfWriter.getWriterConfig().set(JSONLDSettings.HIERARCHICAL_VIEW, true);
			//rdfWriter.getWriterConfig().set(JSONLDSettings.OPTIMIZE, true);
			
			InputStream input = new FileInputStream(Paths.get(Main.mappingFile).toString());
			Model mappingModel = IoUtils.parse(input, RDFFormat.TURTLE);
			Iterator<Namespace> namespaces = mappingModel.getNamespaces().iterator();
			Map<String, String> context = new HashMap<String, String>();
			
			while(namespaces.hasNext()){
				Namespace ns = namespaces.next();
				context.put(ns.getPrefix(), ns.getName());
			}
			
			Rio.write(model, rdfWriter);
			
			Object jsonObject = JsonUtils.fromString(jsonModel.toString());
			
			JsonLdOptions options = new JsonLdOptions();
			Object compact = JsonLdProcessor.compact(jsonObject, context, options);

			outString.append(new JSONObject(removeIds(JsonUtils.toPrettyString(compact))).toString(4));
			
		}else{
			Rio.write(model, outString, determineRdfFormat(Main.outputFormat));
		}

		Writer fileWriter = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
		fileWriter.write(outString.toString());
		fileWriter.flush(); 
		fileWriter.close();
	}
	
	private static String removeIds(String json) {
		//System.out.println(json); 
		return json.replaceAll("\\\"@id\\\"\\s:\\s\\\"_:.*\\\",", "");
	}

	private static void convertFile(InputStream inputStream, boolean useStream, File file) throws Exception{

		Set<TriplesMap> mapping =
				RmlMappingLoader
				.build()
				.load(Paths.get(Main.mappingFile), determineRdfFormat(Main.mappingFormat));

		RmlMapper mapper = null;

		if(Main.inputFile.substring(Main.inputFile.length()-4).equals(".xml")){
			mapper = RmlMapper.newBuilder()
					.setLogicalSourceResolver(Rdf.Ql.XPath, new XPathResolver())
					.build();
		}else if(Main.inputFile.substring(Main.inputFile.length()-4).equals(".csv")){
			mapper = RmlMapper.newBuilder()
					.setLogicalSourceResolver(Rdf.Ql.Csv, new CsvResolver())
					.build();
		}else if(Main.inputFile.substring(Main.inputFile.length()-5).equals(".json")){
			mapper = RmlMapper.newBuilder()
					.setLogicalSourceResolver(Rdf.Ql.JsonPath, new JsonPathResolver())
					.build();
		}else{
			throw new Exception("Extension is not supported!");
		}

		if(useStream){
			mapper.bindInputStream(inputStream);
		}

		try {
			printModel2File(mapper.map(mapping), file);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
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
		cliOptions.addOption("mf", "mapping format", true, 
				"The rdf format used for the mapping (optional)");
		cliOptions.addOption("v", "version", false, 
				"Version of the carml-cli");
		return cliOptions;
	}

	public static void displayHelp() {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("Caramel CLI interface", generateCLIOptions());
		System.exit(1);
	}

	public static void displayVersion() throws IOException {
		InputStream resourceAsStream = Main.class.getResourceAsStream("/META-INF/maven/info.resc.rml.carml/cli/pom.properties");
		Properties prop = new Properties();
		prop.load(resourceAsStream);
		System.out.println(prop.getProperty("artifactId") + "-" + prop.getProperty("version"));
		System.exit(1);
	}
}
