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

import com.dslplatform.json.CompiledJson;
import com.dslplatform.json.DslJson;
import com.dslplatform.json.PrettifyOutputStream;
import com.dslplatform.json.runtime.Settings;
import elf4j.engine.service.LogEvent;
import elf4j.engine.service.util.StackTraces;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import org.slf4j.MDC;

/** */
@Value
@Builder
class JsonElement implements PatternElement {
  private static final String UTF_8 = StandardCharsets.UTF_8.toString();
  private static final int JSON_BYTES_INIT_SIZE = 1024;
  private static final String CALLER_DETAIL = "caller-detail";
  private static final String CALLER_THREAD = "caller-thread";
  private static final String PRETTY = "pretty";
  private static final Set<String> DISPLAY_OPTIONS = Arrays.stream(
          new String[] {CALLER_THREAD, CALLER_DETAIL, PRETTY})
      .collect(Collectors.toSet());
  boolean includeCallerThread;
  boolean includeCallerDetail;
  boolean prettyPrint;

  @ToString.Exclude
  DslJson<Object> dslJson =
      new DslJson<>(Settings.basicSetup().skipDefaultValues(true).includeServiceLoader());

  /**
   * @param patternSegment to convert
   * @return converted patternSegment object
   */
  public static JsonElement from(@NonNull String patternSegment) {
    Optional<String> displayOption = PatternElements.getPatternElementDisplayOption(patternSegment);
    if (!displayOption.isPresent()) {
      return JsonElement.builder().build();
    }
    Set<String> options =
        Arrays.stream(displayOption.get().split(",")).map(String::trim).collect(Collectors.toSet());
    if (!DISPLAY_OPTIONS.containsAll(options)) {
      throw new IllegalArgumentException("Invalid JSON display option inside: " + options);
    }
    return JsonElement.builder()
        .includeCallerThread(options.contains(CALLER_THREAD))
        .includeCallerDetail(options.contains(CALLER_DETAIL))
        .prettyPrint(options.contains(PRETTY))
        .build();
  }

  @Override
  public boolean includeCallerDetail() {
    return this.includeCallerDetail;
  }

  @Override
  public void render(LogEvent logEvent, @NonNull StringBuilder target) {
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(JSON_BYTES_INIT_SIZE);
    try (OutputStream outputStream =
        prettyPrint ? new PrettifyOutputStream(byteArrayOutputStream) : byteArrayOutputStream) {
      dslJson.serialize(JsonLogEntry.from(logEvent, this), outputStream);
      target.append(byteArrayOutputStream.toString(UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Value
  @Builder
  @CompiledJson
  static class JsonLogEntry {
    OffsetDateTime timestamp;
    String level;
    LogEvent.ThreadValue callerThread;
    String callerClass;
    LogEvent.StackFrameValue callerDetail;
    Map<String, String> context;
    String message;
    String exception;

    static JsonLogEntry from(@NonNull LogEvent logEvent, @NonNull JsonElement jsonPattern) {
      return JsonLogEntry.builder()
          .timestamp(OffsetDateTime.ofInstant(logEvent.getTimestamp(), ZoneId.systemDefault()))
          .callerClass(jsonPattern.includeCallerDetail ? null : logEvent.getCallerClassName())
          .level(logEvent.getNativeLogger().getLevel().name())
          .callerThread(jsonPattern.includeCallerThread ? logEvent.getCallerThread() : null)
          .callerDetail(
              jsonPattern.includeCallerDetail
                  ? Objects.requireNonNull(logEvent.getCallerFrame())
                  : null)
          .message(logEvent.getResolvedMessage().toString())
          .context(MDC.getCopyOfContextMap())
          .exception(
              logEvent.getThrowable() == null
                  ? null
                  : StackTraces.getTraceAsBuffer(logEvent.getThrowable()).toString())
          .build();
    }
  }
}
