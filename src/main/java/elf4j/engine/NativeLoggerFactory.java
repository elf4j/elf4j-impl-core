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

package elf4j.engine;

import static java.util.stream.Collectors.toMap;

import elf4j.Level;
import elf4j.Logger;
import elf4j.engine.service.EventingLogService;
import elf4j.engine.service.LogService;
import elf4j.engine.service.LogServiceManager;
import elf4j.engine.service.configuration.LogServiceConfiguration;
import elf4j.engine.service.util.StackTraceUtils;
import elf4j.spi.LoggerFactory;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.NonNull;

/**
 *
 */
public class NativeLoggerFactory implements LoggerFactory, LogServiceManager.Refreshable {
    private static final Level DEFAULT_LOGGER_SEVERITY_LEVEL = Level.INFO;
    /**
     * Made injectable for extensions other than this native ELF4J implementation
     */
    @NonNull
    private final Level defaultLoggerLevel;

    private final Map<Level, Map<String, NativeLogger>> nativeLoggers =
            EnumSet.allOf(Level.class).stream().collect(toMap(Function.identity(), level -> new ConcurrentHashMap<>()));
    /**
     * The class or interface that the API client calls first to get a logger instance. The client caller class of this
     * class will be the declaring class of the logger instances this factory produces.
     * <p></p>
     * For this native implementation, the service access class is the {@link Logger} interface itself as the client
     * calls the static factory method {@link Logger#instance()} first to get a logger instance. If this library is used
     * as the engine of another logging API, then this access class would be the class in that API that the client calls
     * first to get a logger instance of that API.
     */
    @NonNull
    private final Class<?> serviceAccessClass;

    @NonNull
    private final LogServiceFactory logServiceFactory;

    /**
     * Default constructor required by {@link java.util.ServiceLoader}
     */
    public NativeLoggerFactory() {
        this(Logger.class);
    }

    /**
     * @param serviceAccessClass the class or interface that the API client application calls first to a logger
     * instance
     */
    public NativeLoggerFactory(@NonNull Class<?> serviceAccessClass) {
        this(DEFAULT_LOGGER_SEVERITY_LEVEL, serviceAccessClass, new ConfiguredLogServiceFactory());
    }

    NativeLoggerFactory(
            @NonNull Level defaultLoggerLevel,
            @NonNull Class<?> serviceAccessClass,
            @NonNull LogServiceFactory logServiceFactory) {
        this.defaultLoggerLevel = defaultLoggerLevel;
        this.serviceAccessClass = serviceAccessClass;
        this.logServiceFactory = logServiceFactory;
        LogServiceManager.INSTANCE.register(this);
    }

    /**
     * More expensive logger instance creation as it uses stack trace to locate the client class (declaring class)
     * requesting the Logger instance.
     *
     * @return new instance of {@link NativeLogger}
     */
    @Override
    public NativeLogger logger() {
        return getLogger(
                defaultLoggerLevel, StackTraceUtils.callerOf(serviceAccessClass).getClassName());
    }

    @Override
    public void refresh(@Nullable Properties properties) {
        logServiceFactory.reset(properties);
    }

    @Override
    public void refresh() {
        logServiceFactory.reload();
    }

    @NonNull
    LogService getLogService() {
        return logServiceFactory.getLogService();
    }

    NativeLogger getLogger(Level level, String declaringClassName) {
        return nativeLoggers.get(level).computeIfAbsent(declaringClassName, k -> new NativeLogger(k, level, this));
    }

    interface LogServiceFactory {
        LogService getLogService();

        void reload();

        void reset(Properties properties);
    }

    static class ConfiguredLogServiceFactory implements LogServiceFactory {
        private LogService logService;

        private ConfiguredLogServiceFactory() {
            logService = new EventingLogService(LogServiceConfiguration.byLoading());
        }

        @Override
        public LogService getLogService() {
            return logService;
        }

        @Override
        public void reload() {
            logService = new EventingLogService(LogServiceConfiguration.byLoading());
        }

        @Override
        public void reset(Properties properties) {
            logService = new EventingLogService(LogServiceConfiguration.bySetting(properties));
        }
    }
}
