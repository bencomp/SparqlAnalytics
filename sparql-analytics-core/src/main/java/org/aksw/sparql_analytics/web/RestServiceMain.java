package org.aksw.sparql_analytics.web;

import org.aksw.commons.util.jdbc.JdbcUtils;
import org.aksw.sparql_analytics.core.Backend;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.postgresql.ds.PGSimpleDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jolbox.bonecp.BoneCPConfig;
import com.jolbox.bonecp.BoneCPDataSource;
import com.sun.jersey.spi.container.servlet.ServletContainer;


public class RestServiceMain {
	/**
	 * @param exitCode
	 */
	public static void printHelpAndExit(int exitCode) {
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp(RestServiceMain.class.getName(), cliOptions);
		System.exit(exitCode);
	}

	private static final Logger logger = LoggerFactory
			.getLogger(RestServiceMain.class);
	private static final Options cliOptions = new Options();

	/**
	 * @param args
	 *            the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		logger.info("Launching server...");

		/*
		PropertyConfigurator.configure("log4j.properties");
		LogManager.getLogManager().readConfiguration(
				new FileInputStream("jdklog.properties"));
		*/

		CommandLineParser cliParser = new GnuParser();

		cliOptions.addOption("P", "port", true, "Server port");
		cliOptions.addOption("C", "context", true, "Context e.g. /sparqlify");
		cliOptions.addOption("B", "backlog", true, "Maximum number of connections");

		cliOptions.addOption("d", "database", true, "Database name");
		cliOptions.addOption("u", "username", true, "");
		cliOptions.addOption("p", "password", true, "");
		cliOptions.addOption("h", "hostname", true, "");

		cliOptions.addOption("s", "default service uri", true, "");
		cliOptions.addOption("o", "allow override of default service uri", true, "");
		
		CommandLine commandLine = cliParser.parse(cliOptions, args);

		
		// Parsing of command line args
		String portStr = commandLine.getOptionValue("P", "5522");
		//String backLogStr = commandLine.getOptionValue("B", "100");
		//String contextStr = commandLine.getOptionValue("C", "/sparqlify");
		int port = Integer.parseInt(portStr);
		//int backLog = Integer.parseInt(backLogStr);
		
		
		String defaultServiceUri = commandLine.getOptionValue("s", "http://localhost/sparql");
		String allowOverrideServiceUriStr = commandLine.getOptionValue("o", "false");
		Boolean allowOverrideServiceUri = Boolean.parseBoolean(allowOverrideServiceUriStr);
		
		
		String hostName = commandLine.getOptionValue("h", "localhost");
		String dbName = commandLine.getOptionValue("d", "");
		String userName = commandLine.getOptionValue("u", "");
		String passWord = commandLine.getOptionValue("p", "");

		PGSimpleDataSource dataSourceBean = new PGSimpleDataSource();

		dataSourceBean.setDatabaseName(dbName);
		dataSourceBean.setServerName(hostName);
		dataSourceBean.setUser(userName);
		dataSourceBean.setPassword(passWord);

		BoneCPConfig cpConfig = new BoneCPConfig();
		cpConfig.setDatasourceBean(dataSourceBean);
		/*
		cpConfig.setJdbcUrl(dbconf.getDbConnString()); // jdbc url specific to your database, eg jdbc:mysql://127.0.0.1/yourdb
		cpConfig.setUsername(dbconf.getUsername()); 
		cpConfig.setPassword(dbconf.getPassword());
		*/
		
		cpConfig.setMinConnectionsPerPartition(1);
		cpConfig.setMaxConnectionsPerPartition(3);
//		cpConfig.setMinConnectionsPerPartition(1);
//		cpConfig.setMaxConnectionsPerPartition(1);
		
		cpConfig.setPartitionCount(1);
		//BoneCP connectionPool = new BoneCP(cpConfig); // setup the connection pool	

		BoneCPDataSource dataSource = new BoneCPDataSource(cpConfig);

		Backend backend = new Backend(dataSource);

		
		ServletHolder sh = new ServletHolder(ServletContainer.class);

		
		/*
		 * For 0.8 and later the "com.sun.ws.rest" namespace has been renamed to
		 * "com.sun.jersey". For 0.7 or early use the commented out code instead
		 */
		// sh.setInitParameter("com.sun.ws.rest.config.property.resourceConfigClass",
		// "com.sun.ws.rest.api.core.PackagesResourceConfig");
		// sh.setInitParameter("com.sun.ws.rest.config.property.packages",
		// "jetty");
		sh.setInitParameter(
				"com.sun.jersey.config.property.resourceConfigClass",
				"com.sun.jersey.api.core.PackagesResourceConfig");
		sh.setInitParameter("com.sun.jersey.config.property.packages",
				"org.aksw.sparql_analytics.web");

		Server server = new Server(port);
		Context context = new Context(server, "/", Context.SESSIONS);
		context.addServlet(sh, "/*");
		
		
		
		
		context.setAttribute("backend", backend);
		context.setAttribute("dataSource", dataSource);
		context.setAttribute("defaultServiceUri", defaultServiceUri);
		context.setAttribute("allowOverrideServiceUri", allowOverrideServiceUri);

		server.start();
	}

}