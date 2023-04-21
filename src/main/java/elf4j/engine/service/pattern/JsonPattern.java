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
import com.dslplatform.json.JsonWriter;
import com.dslplatform.json.PrettifyOutputStream;
import com.dslplatform.json.runtime.Settings;
import elf4j.engine.service.LogEntry;
import elf4j.engine.service.util.StackTraceUtils;
import lombok.Builder;
import lombok.NonNull;
import lombok.ToString;
import lombok.Value;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
@NotThreadSafe
@Value
@Builder
public class JsonPattern implements LogPattern {
    private static final String CALLER_DETAIL = "caller-detail";
    private static final String CALLER_THREAD = "caller-thread";
    private static final String PRETTY = "pretty";
    private static final Set<String> DISPLAY_OPTIONS =
            Arrays.stream(new String[] { CALLER_THREAD, CALLER_DETAIL, PRETTY }).collect(Collectors.toSet());
    boolean includeCallerThread;
    boolean includeCallerDetail;
    boolean prettyPrint;
    @ToString.Exclude JsonWriter jsonWriter = new DslJson<>(Settings.basicSetup().skipDefaultValues(true)).newWriter();

    /**
     * @param patternSegment
     *         to convert
     * @return converted patternSegment object
     */
    public static JsonPattern from(@NonNull String patternSegment) {
        if (!PatternType.JSON.isTargetTypeOf(patternSegment)) {
            throw new IllegalArgumentException("patternSegment: " + patternSegment);
        }
        Optional<String> displayOption = PatternType.getPatternDisplayOption(patternSegment);
        if (!displayOption.isPresent()) {
            return JsonPattern.builder().build();
        }
        Set<String> options =
                Arrays.stream(displayOption.get().split(",")).map(String::trim).collect(Collectors.toSet());
        if (!DISPLAY_OPTIONS.containsAll(options)) {
            throw new IllegalArgumentException("Invalid JSON display option inside: " + options);
        }
        return JsonPattern.builder()
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
    public boolean includeCallerThread() {
        return this.includeCallerThread;
    }

    @Override
    public void renderTo(LogEntry logEntry, StringBuilder target) {
        jsonWriter.serializeObject(JsonLogEntry.from(logEntry, this));
        try (OutputStream targetOut = getOutputStream(target)) {
            jsonWriter.toStream(targetOut);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            jsonWriter.reset();
        }
    }

    @NonNull
    private OutputStream getOutputStream(StringBuilder target) {
        AppendingOutputStream appendingOutputStream = new AppendingOutputStream(target);
        return this.prettyPrint ? new PrettifyOutputStream(appendingOutputStream) : appendingOutputStream;
    }

    static class AppendingOutputStream extends OutputStream {
        final Appendable appendable;

        AppendingOutputStream(Appendable appendable) {
            this.appendable = appendable;
        }

        @Override
        public void write(int b) throws IOException {
            appendable.append((char) b);
        }

        @Override
        public void write(byte @NonNull [] b, int off, int len) throws IOException {
            for (int i = 0; i < len; i++) {
                appendable.append((char) b[off + i]);
            }
        }
    }

    @Value
    @Builder
    @CompiledJson
    static class JsonLogEntry {
        OffsetDateTime timestamp;
        String level;
        String callerClass;
        LogEntry.ThreadValue callerThread;
        LogEntry.StackFrameValue callerDetail;
        String message;
        String exception;

        static JsonLogEntry from(@NonNull LogEntry logEntry, @NonNull JsonPattern jsonPattern) {
            return JsonLogEntry.builder()
                    .timestamp(OffsetDateTime.ofInstant(logEntry.getTimestamp(), ZoneId.systemDefault()))
                    .callerClass(jsonPattern.includeCallerDetail ? null : logEntry.getCallerClassName())
                    .level(logEntry.getNativeLogger().getLevel().name())
                    .callerThread(jsonPattern.includeCallerThread ? logEntry.getCallerThread() : null)
                    .callerDetail(jsonPattern.includeCallerDetail ? logEntry.getCallerDetail() : null)
                    .message(logEntry.getResolvedMessage().toString())
                    .exception(logEntry.getThrowable() == null ? null :
                            StackTraceUtils.getTraceAsBuffer(logEntry.getThrowable()).toString())
                    .build();
        }
    }
}
