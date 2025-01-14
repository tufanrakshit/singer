/**
 * Copyright 2019 Pinterest, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pinterest.singer.reader;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.pinterest.singer.SingerTestBase;
import com.pinterest.singer.common.LogStream;
import com.pinterest.singer.common.SingerLog;
import com.pinterest.singer.thrift.LogFile;
import com.pinterest.singer.thrift.LogMessageAndPosition;
import com.pinterest.singer.thrift.configuration.SingerLogConfig;
import com.pinterest.singer.thrift.configuration.TextLogMessageType;
import com.pinterest.singer.utils.SingerUtils;
import com.pinterest.singer.utils.TextLogger;

public class TestTextLogFileReader extends SingerTestBase {

  @Test
  public void testReadLogMessageAndPosition() throws Exception {
    String path = FilenameUtils.concat(getTempPath(), "test2.log");
    List<String> dataWritten = generateSampleMessagesToFile(path);

    long inode = SingerUtils.getFileInode(SingerUtils.getPath(path));
    LogFile logFile = new LogFile(inode);
    LogStream logStream = new LogStream(new SingerLog(new SingerLogConfig()), "test");
    LogFileReader reader = new TextLogFileReader(logStream, logFile, path, 0, 8192, 102400, 1,
        Pattern.compile("^.*$"), TextLogMessageType.PLAIN_TEXT, false, false, true, null, null,
        null);
    for (int i = 0; i < 100; i++) {
      LogMessageAndPosition log = reader.readLogMessageAndPosition();
      assertEquals(dataWritten.get(i).trim(), new String(log.getLogMessage().getMessage()));
    }
    reader.close();
  }

  @Test
  public void testReadLogMessageAndPositionWithHostname() throws Exception {
    String path = FilenameUtils.concat(getTempPath(), "test2.log");
    List<String> dataWritten = generateSampleMessagesToFile(path);
    String delimiter = " ";
    String hostname = "test";

    long inode = SingerUtils.getFileInode(SingerUtils.getPath(path));
    LogFile logFile = new LogFile(inode);
    LogStream logStream = new LogStream(new SingerLog(new SingerLogConfig()), "test");
    LogFileReader reader = new TextLogFileReader(logStream, logFile, path, 0, 8192, 102400, 1,
        Pattern.compile("^.*$"), TextLogMessageType.PLAIN_TEXT, false, true, false, hostname,
        delimiter, null);
    for (int i = 0; i < 100; i++) {
      LogMessageAndPosition log = reader.readLogMessageAndPosition();
      String expected = hostname + delimiter + dataWritten.get(i);
      String observed = new String(log.getLogMessage().getMessage());
      assertEquals(expected.length(), observed.length());
      assertEquals(expected, observed);
    }
    reader.close();
  }

  @Test
  public void testReadLogMessageAndPositionMultiRead() throws Exception {
    String path = FilenameUtils.concat(getTempPath(), "test2.log");
    List<String> dataWritten = generateSampleMessagesToFile(path);

    long inode = SingerUtils.getFileInode(SingerUtils.getPath(path));
    LogFile logFile = new LogFile(inode);
    LogStream logStream = new LogStream(new SingerLog(new SingerLogConfig()), "test");
    LogFileReader reader = new TextLogFileReader(logStream, logFile, path, 0, 8192, 102400, 2,
        Pattern.compile("^.*$"), TextLogMessageType.PLAIN_TEXT, false, false, true, null, null,
        null);
    for (int i = 0; i < 100; i = i + 2) {
      LogMessageAndPosition log = reader.readLogMessageAndPosition();
      assertEquals(dataWritten.get(i) + dataWritten.get(i + 1).trim(),
          new String(log.getLogMessage().getMessage()));
    }
    assertNull(reader.readLogMessageAndPosition());
    reader.close();
  }

  @Test
  public void testEnvironmentVariableInjection() throws Exception {
    String path = FilenameUtils.concat(getTempPath(), "test3.log");
    List<String> dataWritten = generateSampleMessagesToFile(path);

    long inode = SingerUtils.getFileInode(SingerUtils.getPath(path));
    LogFile logFile = new LogFile(inode);
    LogStream logStream = new LogStream(new SingerLog(new SingerLogConfig()), "test");
    LogFileReader reader = new TextLogFileReader(logStream, logFile, path, 0, 8192, 102400, 2,
        Pattern.compile("^.*$"), TextLogMessageType.PLAIN_TEXT, false, false, true, "host", null,
        new HashMap<>(ImmutableMap.of("test", ByteBuffer.wrap("value".getBytes()))));
    for (int i = 0; i < 100; i = i + 2) {
      LogMessageAndPosition log = reader.readLogMessageAndPosition();
      assertEquals(3, log.getInjectedHeadersSize());
      assertEquals(dataWritten.get(i) + dataWritten.get(i + 1).trim(),
          new String(log.getLogMessage().getMessage()));
    }
    assertNull(reader.readLogMessageAndPosition());
    reader.close();
    
    reader = new TextLogFileReader(logStream, logFile, path, 0, 8192, 102400, 2,
        Pattern.compile("^.*$"), TextLogMessageType.PLAIN_TEXT, false, false, true, "host", null,
        null);
    for (int i = 0; i < 100; i = i + 2) {
      LogMessageAndPosition log = reader.readLogMessageAndPosition();
      assertEquals(0, log.getInjectedHeadersSize());
      assertEquals(dataWritten.get(i) + dataWritten.get(i + 1).trim(),
          new String(log.getLogMessage().getMessage()));
    }
    reader.close();
  }

  private List<String> generateSampleMessagesToFile(String path) throws FileNotFoundException,
                                                                 IOException {
    TextLogger logger = new TextLogger(path);
    List<String> dataWritten = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      StringBuilder builder = new StringBuilder();
      for (int j = 0; j < ThreadLocalRandom.current().nextInt(10, 20); j++) {
        builder.append(UUID.randomUUID().toString());
      }
      builder.append('\n');
      String str = builder.toString();
      dataWritten.add(str);
      logger.logText(str);
    }
    return dataWritten;
  }
}