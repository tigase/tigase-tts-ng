/*
 * Tigase Jabber/XMPP Server
 *  Copyright (C) 2004-2019 "Tigase, Inc." <office@tigase.com>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License,
 *  or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program. Look for COPYING file in the top folder.
 *  If not, see http://www.gnu.org/licenses/.
 *
 */

package tigase.tests.dao;

import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.internal.util.Mimetype;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import tigase.xml.Element;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class S3ReportsDao
		extends AbstractDao {

	private final static Pattern bucketPattern = Pattern.compile("arn:aws:s3:::(.*?)/.*");
	private final static Pattern pathPattern = Pattern.compile("arn:aws:s3:::.*?/(.*)");
	private final static Pattern keyPattern = Pattern.compile(".*/(.*?)/");
	private final String bucket;
	private final S3Client client;

	private final static Optional<String> getDataUsingPattern(Pattern pattern, String stringToMatch) {
		final Matcher matcher = pattern.matcher(stringToMatch);
		if (matcher.matches() && matcher.groupCount() > 0) {
			return Optional.of(matcher.group(1));
		} else {
			return Optional.empty();
		}
	}

	public S3ReportsDao(String uri) {
		super(getDataUsingPattern(pathPattern, uri).orElseThrow(() -> new IllegalArgumentException(
				"provided URI didn't contain path that would match pattern: " + pathPattern)));
		bucket = getDataUsingPattern(bucketPattern, uri).orElseThrow(() -> new IllegalArgumentException(
				"provided URI didn't contain bucket that would match pattern: " + bucketPattern));

		client = S3Client.builder().credentialsProvider(EnvironmentVariableCredentialsProvider.create()).build();
	}

	@Override
	public void writeIndexFile(Consumer<Writer> consumer) {
		final StringWriter stringWriter = new StringWriter();
		consumer.accept(stringWriter);

		try {
			final PutObjectRequest request = PutObjectRequest.builder()
					.bucket(bucket)
					.key(resultsPath + "index.html")
					.contentType(Mimetype.MIMETYPE_HTML)
					.build();
			final RequestBody requestBody = RequestBody.fromString(stringWriter.toString());
			final PutObjectResponse putObjectResponse = client.putObject(request, requestBody);
		} catch (Exception e) {
			log.log(Level.WARNING, "Can't write index file", e);
		}
	}

	@Override
	public void storeAsset(String assetPath, String mime, InputStream inputStream) {
		final Path target = Paths.get(resultsPath, assetPath);

		try {
			final PutObjectRequest request = PutObjectRequest.builder()
					.bucket(bucket)
					.key(target.toString())
					.contentType(mime)
					.build();
			final RequestBody requestBody = RequestBody.fromInputStream(inputStream, inputStream.available());
			client.putObject(request, requestBody);
		} catch (Exception e) {
			log.log(Level.WARNING, "Can't store asset: " + assetPath, e);
		}
	}

	@Override
	List<String> getNamesInPath(Path path, FilenameFilter filenameFilter) {
		final String pathPrefix = path.toString() + "/";
		ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
				.bucket(bucket)
				.delimiter("/")
				.prefix(pathPrefix)
				.build();

		ListObjectsV2Response response = client.listObjectsV2(listObjectsV2Request);

		return response.commonPrefixes()
				.stream()
				.map(prefix -> getDataUsingPattern(keyPattern, prefix.prefix()))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());
	}

	@Override
	TestReport getTestReportFromFile(Path results, String version, String testType, String database) {
		try (ResponseInputStream<GetObjectResponse> stream = client.getObject(
				req -> req.bucket(bucket).key(results.toString()).build());
			 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream))) {

			String lines = bufferedReader.lines().collect(Collectors.joining());
			final Optional<Element> element = DaoHelper.parseData(lines);
			if (element.isPresent()) {
				log.log(Level.FINEST, "Parsing for version: {0}, type: {1}, database: {2}, data: {3}", new Object[]{version, testType, database, lines});
				final Results metrics = DaoHelper.getResults(element.get());
				final Optional<LocalDate> finishedAt = DaoHelper.geFinishedTimeFromData(element.get());
				return new TestReport(testType, version, database, metrics, finishedAt.orElse(LocalDate.now()));
			} else {
				log.log(Level.WARNING, "Parsing failed for version: {0}, type: {1}, database: {2}, data: {3}", new Object[]{version, testType, database, lines});
			}
		} catch (IOException e) {
			log.log(Level.WARNING, "Problem getting test data from: " + results, e);
		}
		return null;

	}

	@Override
	boolean fileExists(Path path) {
		try (ResponseInputStream<GetObjectResponse> object = client.getObject(
				GetObjectRequest.builder().bucket(bucket).key(path.toString()).build())) {
			final GetObjectResponse response = object.response();
			return response != null && response.contentLength() > 0;
		} catch (NoSuchKeyException | IOException e) {
			return false;
		}
	}

	@Override
	TestReport getDummyTestReportFromLogFile(String version, String testType, String database, Path logFile) {

		try (ResponseInputStream<GetObjectResponse> stream = client.getObject(
				req -> req.bucket(bucket).key(logFile.toString()).range("2048").build());
			 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(stream))) {

			final GetObjectResponse response = stream.response();
			LocalDate finishedAt = LocalDateTime.ofInstant(response.lastModified(), ZoneOffset.UTC).toLocalDate();

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
			return null;
		}
	}
}
