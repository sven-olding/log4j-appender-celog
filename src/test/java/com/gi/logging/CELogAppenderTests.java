package com.gi.logging;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CELogAppenderTests {
    private static Logger logger;
    private static CELogAppender appender;

    @BeforeAll
    public static void setup() {
        logger = LogManager.getLogger(CELogAppenderTests.class.getName());
        LoggerContext loggerContext = LoggerContext.getContext(false);
        Configuration config = loggerContext.getConfiguration();
        PatternLayout layout = PatternLayout.newBuilder()
                .withPattern("%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n")
                .build();
        appender = CELogAppender
                .newBuilder()
                .withTarget("Domino/SOL!!Projects/DZPBANK/Kredit/GI89/dokumentendruck.nsf")
                .withLayout(layout)
                .withName("CElogAppenderTests")
                .build();
        appender.start();
        config.addAppender(appender);

        AppenderRef appenderRef = AppenderRef.createAppenderRef("CELogAppenderTests", null, null);
        AppenderRef[] appenderRefs = new AppenderRef[]{appenderRef};
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL,
                CELogAppenderTests.class.getName(), "true", appenderRefs, null, config, null);

        loggerConfig.addAppender(appender, null, null);
        config.addLogger(CELogAppenderTests.class.getName(), loggerConfig);
        loggerContext.updateLoggers();
    }

    @AfterAll
    public static void shutdown() {
        LogManager.shutdown();
    }

    @Test
    public void testWarning() {
        logger.warn("warning...");
        assertEquals(1, appender.getWarningCount());
    }

    @Test
    public void testError() {
        logger.error("error!");
        assertEquals(1, appender.getErrorCount());
    }

    @Test
    public void testEntryCount() {
        logger.info("some message");
        logger.info("another message");
        assertEquals(2, appender.getEntryCount());
    }

    @Test
    public void testMaxEntries() {
        for(int i=0;i < (CELogAppender.MAX_ENTRIES + 2);i++) {
            logger.info("log entry number " + i);
        }
        assertEquals(2, appender.getLogNumber());
    }
}
