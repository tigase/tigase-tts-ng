/*
 * Tigase TTS-NG - Test suits for Tigase XMPP Server
 * Copyright (C) 2004 Tigase, Inc. (office@tigase.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, version 3 of the License.
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
package tigase.tests.dao;

import tigase.xml.Element;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DirectoryReportsDao
		extends AbstractDao {

	DirectoryReportsDao(String path) {
		super(path);
	}

	@Override
	public void writeIndexFile(Consumer<Writer> consumer) {
		final Path path = Paths.get(resultsPath, "index.html");
		try (Writer w = Files.newBufferedWriter(path)) {
			consumer.accept(w);
		} catch (IOException e) {
			log.log(Level.SEVERE, "Problem writing index file", e);
		}
	}

	@Override
	public void storeAsset(String assetPath, String mime, InputStream inputStream) throws IOException {
		final Path target = Paths.get(resultsPath, assetPath);
		if (!target.toFile().exists()) {
			target.toFile().mkdirs();
		}
		Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
	}

	TestReport getDummyTestReportFromLogFile(String version, String testType, String database, Path logFile) {

		try (final FileReader fileReader = new FileReader(logFile.toFile());
			 final BufferedReader bufferedReader = new BufferedReader(fileReader)) {

			final BasicFileAttributes attr = Files.readAttributes(logFile, BasicFileAttributes.class);
			final FileTime fileTime = attr.creationTime();

			LocalDate finishedAt = LocalDateTime.ofInstant(fileTime.toInstant(), ZoneOffset.UTC).toLocalDate();

			final Pattern compile = Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}).*");
			String line;
			while ((line = bufferedReader.readLine()) != null) {
				final Matcher matcher = compile.matcher(line);
				if (matcher.matches() && matcher.groupCount() > 0) {
					finishedAt = LocalDate.parse(matcher.group(1), DateTimeFormatter.ISO_DATE);
					break;
				}
			}
			return new TestReport(testType, version, database, null, finishedAt);
		} catch (IOException e) {
			log.log(Level.WARNING, "Can't get test report from file: " + logFile, e);
		}
		return null;
	}

	TestReport getTestReportFromFile(Path resultsPath, String version, String testType, String database) {
		try {
			String lines = String.join("", Files.readAllLines(resultsPath));
			log.log(Level.FINEST, "Parsing from path: {0}, data: {1}", new Object[]{resultsPath, lines});
			final Optional<Element> element = DaoHelper.parseData(lines);
			if (element.isPresent()) {
				final Results metrics = DaoHelper.getResults(element.get());
				final Optional<LocalDate> finishedAt = DaoHelper.geFinishedTimeFromData(element.get());
				return new TestReport(testType, version, database, metrics, finishedAt.orElse(LocalDate.now()));
			} else {
				log.log(Level.WARNING, "Parsing failed for version: {0}, type: {1}, database: {2}, data: {3}", new Object[]{version, testType, database, lines});
			}
		} catch (IOException e) {
			log.log(Level.WARNING, "Can't get test report from file: " + resultsPath);
		}
		return null;
	}

	@Override
	boolean fileExists(Path path) {
		return path.toFile().exists();
	}

	protected List<String> getNamesInPath(Path path, FilenameFilter filenameFilter) {
		if (Files.exists(path)) {
			return Arrays.stream(path.toFile().list(filenameFilter))
					.parallel()
					.filter(p -> !p.startsWith("."))
					.collect(Collectors.toList());
		}
		return Collections.emptyList();
	}
}
