/*
 * MIT License
 *
 * Copyright (c) 2023 Qingtian Wang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package elf4j.engine.service.pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

import elf4j.Level;
import elf4j.engine.NativeLogServiceProvider;
import elf4j.engine.NativeLogger;
import elf4j.engine.service.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageAndExceptionPatternTest {
  @Mock
  NativeLogServiceProvider mockNativeLogServiceProvider;

  LogEvent mockLogEvent;
  String mockMessage = "testLogMessage {}";
  Object[] mockArgs = new Object[] {"testArg1"};
  Exception mockException = new Exception("testExceptionMessage");

  @BeforeEach
  void beforeEach() {
    mockLogEvent = LogEvent.builder()
        .nativeLogger(new NativeLogger("testLoggerName", Level.ERROR, mockNativeLogServiceProvider))
        .callerThread(new LogEvent.ThreadValue(
            Thread.currentThread().getName(), Thread.currentThread().getId()))
        .callerFrame(LogEvent.StackFrameValue.from(
            new StackTraceElement("testClassName", "testMethodName", "testFileName", 42)))
        .message(mockMessage)
        .arguments(mockArgs)
        .throwable(mockException)
        .build();
  }

  @Nested
  class render {
    @Test
    void includeBothMessageAndException() {
      MessageAndExceptionElement messageAndExceptionPattern = new MessageAndExceptionElement();
      StringBuilder logText = new StringBuilder();

      messageAndExceptionPattern.render(mockLogEvent, logText);
      String rendered = logText.toString();

      assertTrue(rendered.contains(mockLogEvent.getResolvedMessage()));
      assertTrue(rendered.contains(mockException.getMessage()));
    }
  }
}
