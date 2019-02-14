/*
 * SummaryGenarator.groovy
 *
 * Tigase TTS-NG
 * Copyright (C) 2015-2017 "Tigase, Inc." <office@tigase.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License,
 * or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. Look for COPYING file in the top folder.
 * If not, see http://www.gnu.org/licenses/.
 */

import groovy.cli.commons.CliBuilder
import groovy.text.Template
import groovy.text.markup.MarkupTemplateEngine
import groovy.text.markup.TemplateConfiguration
import groovy.xml.XmlUtil
import tigase.util.Version

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SummaryGenarator {

	static String rootDirectory

	static void main(String[] args) {

		def cli = new CliBuilder()
		cli.p(type: String, 'Path to results', required: true)
		cli.v(type: String, 'Version', required: true)
		cli.d(type: String, 'Database used', args: -2, valueSeparator: ' ', required: true)

		def options = cli.parse(args)
		if (!options) {
			return
		}

		Version paramVersion = Version.of(options.v);

		def start = System.currentTimeMillis()

		rootDirectory = options.p
		def resultsDirectory = "${rootDirectory}/test-results"

		if (!(new File("${resultsDirectory}")).exists()) {
			println("Results directory doesn't exists")
			System.exit(3)
		}

		def times = []
		ArrayList<TestCase> testResults = new ArrayList<TestCase>()
		getVersions(resultsDirectory).each { Version ver ->
			getTestGroups(resultsDirectory, ver).each { String test ->
				getDatabases(resultsDirectory, ver, test).each { String db ->
					def startint = System.currentTimeMillis()
					def metrics = getTestsMetrics(resultsDirectory, ver, test, db)
					def logFile = getLogFile(resultsDirectory, ver, test, db)
					def reportFile = getReportFile(resultsDirectory, ver, test, db)
					if (metrics || logFile || reportFile) {
						def date = getTestDate(metrics, logFile)
						TestCase tc = new TestCase(test, ver, db, metrics, logFile, reportFile, date)
						testResults.add(tc)
					}
					def stop = (System.currentTimeMillis() - startint) / 1000
					times.add(stop);
				}
			}
		}
		
		def average = times.sum() / times.size()
		println sprintf("average time: %ss, total: ", average, times.size())
		println sprintf("results parsing generation time: %ss", (System.currentTimeMillis() - start) / 1000)

		Map<String, Map<Version, Map<String, List<TestCase>>>> map
		map = testResults.groupBy({ it.testType }, { it.version }, { it.dbType })
		println sprintf("Group by time: %ss", (System.currentTimeMillis() - start) / 1000)

		Map<String, Map<String, Map<String, TestCase>>> collectible
		collectible = map.collectEntries { String testType, Map<Version, Map<String, List<TestCase>>> versions ->
			[ (testType): versions.collectEntries { Version version, Map<String, List<TestCase>> databaseTypes ->
				[ (version): databaseTypes.collectEntries { String dbType, List<TestCase> testCases ->
					testCases.collectEntries { TestCase tc -> [ (dbType): tc ]
					}
				} as TreeMap ]
			} as TreeMap ]
		} as TreeMap

		println sprintf("collecting entries generation time: %ss", (System.currentTimeMillis() - start) / 1000)
		def testAllDBs = testResults
				.groupBy({ it.testType }, { it.dbType })
				.collectEntries { testType, dbtypes -> [ (testType): dbtypes.keySet() ] }

		def resultHtmlPage = new File("${rootDirectory}/index.html")
		if (!resultHtmlPage.getParentFile().exists()) {
			if (!resultHtmlPage.getParentFile().mkdirs()) {
				println("Directory creation failed! Path: ${resultHtmlPage.getParentFile()}")
				System.exit(2)
			}
		}

		def pw = new PrintWriter(resultHtmlPage)
		TemplateConfiguration config = new TemplateConfiguration()
		config.setAutoIndent(true)
		config.setAutoNewLine(true)
		MarkupTemplateEngine engine = new MarkupTemplateEngine(config)
		Template template = engine.createTemplate(new File("scripts/templates/index.tpl"))
		Map<String, Object> model = [ tests: collectible, testAllDBs: testAllDBs ]
		Writable output = template.make(model)
		output.writeTo(pw)
		pw.close()

		println sprintf("summary page generation time: %ss", (System.currentTimeMillis() - start) / 1000)
		Path source = Paths.get("scripts/templates/assets")
		def destination = Paths.get("${rootDirectory}/assets")

		Files.walk(source, Integer.MAX_VALUE).each {
			Path dest = destination.resolve(source.relativize(it))
			if (!it.toFile().isDirectory()) {
				if (it.fileName ==~ "^.*\\.(css|png|woff)\$") {
					Files.copy(it, dest, StandardCopyOption.REPLACE_EXISTING)
				}
			} else {
				dest.toFile().mkdirs()
			}
		}

		println sprintf("total generation time: %ss", (System.currentTimeMillis() - start) / 1000)

		def resultsForVersion = testResults
				.findAll { tc -> tc?.version == paramVersion && options.ds.contains(tc?.dbType )}

		if (resultsForVersion.isEmpty()) {
			println("No test results matching version/database!")
			System.exit(1)
		} else {
			def failsTotal = resultsForVersion.count { it -> (it.getTestResult() != TestCase.Result.PASSED) }

			println("fails total: " + failsTotal)
			System.exit(failsTotal > 0 ? 1 : 0)
		}

	}

	static Map<String, Object> getTestsMetrics(path, version, test, db) {
		def file = new File("${path}/${version}/${test}/${db}/testng-results.xml")
		def result = null


		if (file.exists()) {
			def parsed = new XmlSlurper().parse(file)
			result = [ total   : parsed.@total.toInteger(),
					   failed  : parsed.@failed.toInteger(),
					   ignored : parsed.@ignored.toInteger(),
					   passed  : parsed.@passed.toInteger(),
					   skipped : parsed.@skipped.toInteger(),
					   duration: (parsed.suite.collect { it.@"duration-ms".toInteger() }.sum() / 1000).toInteger() ]
			if (parsed.suite[0]?.@"finished-at") {
				result['finishedAt'] = parsed.suite[0].@"finished-at".toString()
			}
			parsed."reporter-output".replaceNode{}
			parsed.suite.test*.replaceNode{}
			parsed.suite.groups*.replaceNode{}
			file.write(XmlUtil.serialize(parsed))
		}

		return result
	}

	static File getLogFile(path, version, test, db) {
		def file = new File("${path}/${version}/${test}/${db}/server-log/tigase-console.log")
		return file?.exists() ? file : null
	}

	static File getReportFile(path, version, test, db) {
		def file = new File("${path}/${version}/${test}/${db}/html/index.html")
		return file?.exists() ? file : null
	}

	static LocalDate getTestDate(Map metrics, File logFile) {
		if (metrics?.finishedAt) {
//			def parse = LocalDate.parse(metrics?.finishedAt,
//										DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.of("UTC")))
			// above throws exception under some OSs hence using forced pattern
			def parse = LocalDate.parse(metrics?.finishedAt,
										DateTimeFormatter.ofPattern("yyyy-MM-dd\'T\'HH:mm:ss\'Z\'"))
			metrics.remove('finishedAt')
			return parse
		} else if (logFile?.exists()) {
			return LocalDate.from(Files.readAttributes(logFile.toPath(), BasicFileAttributes.class)
										  .creationTime()
										  .toInstant()
										  .atZone(ZoneId.of("UTC"))
										  .toLocalDate())
		} else {
			return null
		}
	}

	static List<Version> getVersions(String path) {
		return getItems(path, "(\\d\\.){2}\\d(-(SNAPSHOT|RC)\\d*)?-b\\d{4,}"). collect { ver -> Version.of(ver) }
	}

	static String[] getTestGroups(rootPath, ver) {
		return getItems("${rootPath}/${ver}", "(all|tigase\\..*)")

	}

	static String[] getDatabases(rootPath, ver, test) {
		return getItems("${rootPath}/${ver}/${test}", "(.*)")
	}

	private static String[] getItems(String path, String regex) {
		def items = [ ]
		new File(path).eachDirMatch(~regex) { dir -> items.add(dir.name)
		}

		return items
	}
}

class TestCase {

	TestCase(String testType, Version version, String dbType, Map metrics, File logFile, File reportFile,
			 LocalDate date) {
		this.testType = testType
		this.version = version
		this.dbType = dbType
		this.metrics = metrics
		this.logFile = logFile
		this.reportFile = reportFile
		this.finishedDate = date
	}

	String testType
	Version version
	String dbType
	Map metrics
	File logFile
	File reportFile
	LocalDate finishedDate

	@Override
	String toString() {
		final StringBuilder sb = new StringBuilder()

		sb.append(version).append(": ")
		if (metrics) {
			metrics.each { k, v -> sb.append(k).append(": ").append(v).append("\n") }
		}
		sb.append(dbType).append(", ")
		sb.append(finishedDate).append(", ")
		sb.append(getMetricsAsString()).append(", ")
		if (logFile) {
			sb.append("log: ").append(logFile)
		}
		return sb.toString()
//		return "${version}: ${dbType}"
	}

	def getMetricsAsString() {
		return metrics ? "${metrics.passed} / ${metrics.failed} | ${metrics.total}" : null
	}

	def getDuration() {
		return metrics ? DateTimeFormatter.ofPattern("HH:mm:ss.S").format(LocalTime.ofSecondOfDay(metrics.duration)) :
			   null
	}

	def getLogFile() {
		return logFile ? Paths.get(SummaryGenarator.rootDirectory).relativize(logFile.toPath()) : null
	}

	def getReportFile() {
		return reportFile ? Paths.get(SummaryGenarator.rootDirectory).relativize(reportFile.toPath()) : null
	}

	Result getTestResult() {
		if (metrics?.failed > 0) {
			return Result.FAILED
		} else {
			return Result.PASSED
		}
	}

	enum Result {

		FAILED("warning"),
		PASSED("success")

		private String cssClass

		Result(String colour) {
			cssClass = colour
		}

		String getCssClass() {
			return cssClass
		}
	}
}

