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

package elf4j.engine.writer.pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import elf4j.engine.service.LogEntry;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
@Value
@Builder
public class JsonPatternSegment implements LogPattern {
    private static final String CALLER_DETAIL = "caller-detail";
    private static final String CALLER_THREAD = "caller-thread";
    private static final String PRETTY = "pretty";
    private static final Set<String> DISPLAY_OPTIONS =
            Arrays.stream(new String[] { CALLER_THREAD, CALLER_DETAIL, PRETTY }).collect(Collectors.toSet());
    boolean includeCallerThread;
    boolean includeCallerDetail;
    ObjectMapper objectMapper;

    /**
     * @param patternSegment to convert
     * @return converted patternSegment object
     */
    public static JsonPatternSegment from(@NonNull String patternSegment) {
        if (!PatternSegmentType.JSON.isTargetTypeOf(patternSegment)) {
            throw new IllegalArgumentException("patternSegment: " + patternSegment);
        }
        Optional<String> patternOption = PatternSegmentType.getPatternSegmentOption(patternSegment);
        ObjectMapper objectMapper = new ObjectMapper().disable(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        if (!patternOption.isPresent()) {
            return JsonPatternSegment.builder()
                    .includeCallerThread(false)
                    .includeCallerDetail(false)
                    .objectMapper(objectMapper)
                    .build();
        }
        Set<String> options =
                Arrays.stream(patternOption.get().split(",")).map(String::trim).collect(Collectors.toSet());
        if (!DISPLAY_OPTIONS.containsAll(options)) {
            throw new IllegalArgumentException("Invalid JSON display option inside: " + options);
        }
        return JsonPatternSegment.builder()
                .includeCallerThread(options.contains(CALLER_THREAD))
                .includeCallerDetail(options.contains(CALLER_DETAIL))
                .objectMapper(options.contains(PRETTY) ? objectMapper.enable(SerializationFeature.INDENT_OUTPUT) :
                        objectMapper)
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
    public void render(LogEntry logEntry, StringBuilder logTextBuilder) {
        StringWriter stringWriter = new StringWriter();
        try {
            objectMapper.writeValue(stringWriter, JsonLogEntry.from(logEntry, this));
            logTextBuilder.append(stringWriter.getBuffer());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Value
    @Builder
    static class JsonLogEntry {
        static final DateTimeFormatter DATE_TIME_FORMATTER =
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
        String timestamp;
        String level;
        LogEntry.ThreadInformation callerThread;
        String callerClass;
        LogEntry.StackTraceFrame callerDetail;
        String message;
        Throwable exception;

        static JsonLogEntry from(@NonNull LogEntry logEntry, @NonNull JsonPatternSegment jsonPatternSegment) {
            return JsonLogEntry.builder()
                    .timestamp(DATE_TIME_FORMATTER.format(logEntry.getTimestamp()))
                    .callerClass(jsonPatternSegment.includeCallerDetail ? null : logEntry.getCallerClassName())
                    .level(logEntry.getNativeLogger().getLevel().name())
                    .callerThread(jsonPatternSegment.includeCallerThread ? logEntry.getCallerThread() : null)
                    .callerDetail(jsonPatternSegment.includeCallerDetail ? logEntry.getCallerFrame() : null)
                    .message(logEntry.getResolvedMessage())
                    .exception(logEntry.getException())
                    .build();
        }
    }
}
