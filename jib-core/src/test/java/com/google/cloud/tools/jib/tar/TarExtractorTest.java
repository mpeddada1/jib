/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.tar;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.io.Resources;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Tests for {@link TarExtractor}. */
public class TarExtractorTest {

  @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

  @Test
  public void testExtract() throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/extract.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();
    TarExtractor.extract(source, destination);

    Assert.assertTrue(Files.exists(destination.resolve("file A")));
    Assert.assertTrue(Files.exists(destination.resolve("file B")));
    Assert.assertTrue(
        Files.exists(destination.resolve("folder").resolve("nested folder").resolve("file C")));

    try (Stream<String> lines = Files.lines(destination.resolve("file A"))) {
      String contents = lines.collect(Collectors.joining());
      Assert.assertEquals("Hello", contents);
    }
  }

  @Test
  public void testExtract_missingDirectoryEntries() throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/extract-missing-dirs.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();
    TarExtractor.extract(source, destination);

    Assert.assertTrue(Files.exists(destination.resolve("world")));
    Assert.assertTrue(
        Files.exists(destination.resolve("a").resolve("b").resolve("c").resolve("world")));

    try (Stream<String> lines = Files.lines(destination.resolve("world"))) {
      String contents = lines.collect(Collectors.joining());
      Assert.assertEquals("world", contents);
    }
  }

  @Test
  public void testExtract_symlinks() throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/symlinks.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();
    TarExtractor.extract(source, destination);

    Assert.assertTrue(Files.isDirectory(destination.resolve("directory1")));
    Assert.assertTrue(Files.isDirectory(destination.resolve("directory2")));
    Assert.assertTrue(Files.isRegularFile(destination.resolve("directory2/regular")));
    Assert.assertTrue(Files.isSymbolicLink(destination.resolve("directory-symlink")));
    Assert.assertTrue(Files.isSymbolicLink(destination.resolve("directory1/file-symlink")));
  }

  @Test
  public void testExtract_modificationTimePreserved() throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/extract.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();

    TarExtractor.extract(source, destination);

    assertThat(Files.getLastModifiedTime(destination.resolve("file A")))
        .isEqualTo(FileTime.from(Instant.parse("2019-08-01T16:13:09Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("file B")))
        .isEqualTo(FileTime.from(Instant.parse("2019-08-01T16:12:00Z")));
    assertThat(
            Files.getLastModifiedTime(
                destination.resolve("folder").resolve("nested folder").resolve("file C")))
        .isEqualTo(FileTime.from(Instant.parse("2019-08-01T16:12:21Z")));
  }

  @Test
  public void testExtract_symlinks_modificationTimePreserved()
      throws URISyntaxException, IOException {
    Path source = Paths.get(Resources.getResource("core/symlinks.tar").toURI());
    Path destination = temporaryFolder.getRoot().toPath();

    Map<String, FileTime> originalModificationTimeMap = createModificationTimeMap(source);

    TarExtractor.extract(source, destination);

    // Validate that the symlink's modification time is set to the target's.
    assertThat(originalModificationTimeMap.get("directory1/"))
        .isEqualTo(FileTime.from(Instant.parse("2020-10-16T21:09:46Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("directory1")))
        .isEqualTo(FileTime.from(Instant.parse("2020-10-16T21:09:46Z")));
    assertThat(originalModificationTimeMap.get("directory-symlink"))
        .isEqualTo(FileTime.from(Instant.parse("2020-10-16T21:09:23Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("directory-symlink")))
        .isEqualTo(FileTime.from(Instant.parse("2020-10-16T21:09:46Z")));

    assertThat(originalModificationTimeMap.get("directory2/regular"))
        .isEqualTo(FileTime.from(Instant.parse("2020-10-16T21:09:54Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("directory2/regular")))
        .isEqualTo(FileTime.from(Instant.parse("2020-10-16T21:09:54Z")));
    assertThat(originalModificationTimeMap.get("directory1/file-symlink"))
        .isEqualTo(FileTime.from(Instant.parse("2020-10-16T21:09:46Z")));
    assertThat(Files.getLastModifiedTime(destination.resolve("directory1/file-symlink")))
        .isEqualTo(FileTime.from(Instant.parse("2020-10-16T21:09:54Z")));
  }

  private static Map<String, FileTime> createModificationTimeMap(Path source) throws IOException {
    Map<String, FileTime> modificationTimeMap = new HashMap();
    try (InputStream in = new BufferedInputStream(Files.newInputStream(source));
        TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(in)) {
      for (TarArchiveEntry entry = tarArchiveInputStream.getNextTarEntry();
          entry != null;
          entry = tarArchiveInputStream.getNextTarEntry()) {
        modificationTimeMap.put(entry.getName(), FileTime.from(entry.getModTime().toInstant()));
      }
      return modificationTimeMap;
    }
  }
}
