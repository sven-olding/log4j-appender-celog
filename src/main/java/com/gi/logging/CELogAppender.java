package com.gi.logging;

import lotus.domino.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LoggingException;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginBuilderFactory;
import org.apache.logging.log4j.core.config.plugins.validation.constraints.Required;
import org.owasp.encoder.Encode;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Plugin(name = "CELogAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class CELogAppender extends AbstractAppender {
    public static final int MAX_ENTRIES = 500;
    private final String targetDbPath;
    private final ConcurrentMap<String, String> warnings = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> errors = new ConcurrentHashMap<>();
    private Database targetDb;
    private Document logDoc;
    private MIMEEntity mimeEntity;
    private Stream stream;
    private int logNumber = 0;
    private long entryCount = 0;
    private long warningCount = 0;
    private long errorCount = 0;
    private Session session;

    protected CELogAppender(String name, Filter filter, Layout<? extends Serializable> layout,
                            boolean ignoreExceptions, Property[] properties, String target) {
        super(name, filter, layout, ignoreExceptions, properties);
        targetDbPath = target;
    }

    @PluginBuilderFactory
    public static <B extends Builder<B>> B newBuilder() {
        return new Builder<B>().asBuilder();
    }

    public ConcurrentMap<String, String> getWarnings() {
        return warnings;
    }

    public ConcurrentMap<String, String> getErrors() {
        return errors;
    }

    @Override
    public void append(LogEvent event) {
        byte[] content = getLayout().toByteArray(event);
        String message = new String(content, StandardCharsets.UTF_8);
        String cssAddon = "color: #000";
        if (event.getLevel() == Level.WARN) {
            warnings.put(Instant.now().toString(), message);
            cssAddon = "color: #0000FF; font-weight: bold";
            warningCount++;
        }
        if (event.getLevel() == Level.ERROR || event.getLevel() == Level.FATAL) {
            errors.put(Instant.now().toString(), message);
            cssAddon = "color: #FF0000; font-weight: bold";
            errorCount++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family: Arial; font-size: 10px; margin-left: 5px; margin-right: 5px; ")
                .append(cssAddon).append("\">");
        message = Encode.forHtmlContent(message).replace("\n", "<br />").replace("\r", "");
        sb.append(message);
        sb.append("<br />");
        sb.append("</div>");

        try {
            stream.writeText(sb.toString());
        } catch (NotesException e) {
            System.out.println(message);
            e.printStackTrace();
        }

        entryCount++;
        if (entryCount >= MAX_ENTRIES) {
            saveLogDoc();
            createNewLogDoc();
        }
    }

    private void createNewLogDoc() {
        try {
            logNumber = getLogNumber() + 1;
            entryCount = 0;

            logDoc = targetDb.createDocument();

            logDoc.replaceItemValue("Form", "faCELog");
            logDoc.replaceItemValue("fdLogDate", session.createDateTime(new Date()));
            logDoc.replaceItemValue("fdLogEvent", getName());
            logDoc.replaceItemValue("fdUser", session.getEffectiveUserName());
            logDoc.replaceItemValue("fdLogNumber", getLogNumber());
            logDoc.replaceItemValue("fdDBName", targetDb.getTitle());

            mimeEntity = logDoc.createMIMEEntity("fdLogEntrys");
            stream = session.createStream();
        } catch (NotesException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start() {
        super.start();
        try {
            NotesThread.sinitThread();
            session = NotesFactory.createSession();
            String[] split = targetDbPath.split("!!");
            String server = split[0];
            String path = split[1];
            targetDb = session.getDatabase(server, path, false);
            if (!targetDb.isOpen()) {
                targetDb.open();
            }
            createNewLogDoc();
        } catch (NotesException e) {
            throw new LoggingException(e);
        }
    }

    private void saveLogDoc() {
        try {
            if (mimeEntity != null && stream != null) {
                mimeEntity.setContentFromText(stream, "text/html;charset=UTF-8", MIMEEntity.ENC_IDENTITY_8BIT);
                stream.close();
            }
        } catch (NotesException e) {
            e.printStackTrace();
        }
        try {
            logDoc.replaceItemValue("fdLogErrors", getErrorCount());
            logDoc.replaceItemValue("fdLogWarnings", getWarningCount());
            logDoc.closeMIMEEntities(true);
            logDoc.computeWithForm(false, false);
            logDoc.save();
        } catch (NotesException e) {
            e.printStackTrace();
        } finally {
            try {
                logDoc.recycle();
            } catch (NotesException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
        saveLogDoc();

        try {
            targetDb.recycle();
        } catch (NotesException e) {
            e.printStackTrace();
        }

        NotesThread.stermThread();

        return super.stop(timeout, timeUnit);
    }

    public long getWarningCount() {
        return warningCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public long getEntryCount() {
        return entryCount;
    }

    public int getLogNumber() {
        return logNumber;
    }

    public static class Builder<B extends Builder<B>> extends AbstractAppender.Builder<B>
            implements org.apache.logging.log4j.core.util.Builder<CELogAppender> {

        @PluginBuilderAttribute
        @Required(message = "No target database provided")
        private String target;

        public B withTarget(final String target) {
            this.target = target;
            return asBuilder();
        }

        @Override
        public CELogAppender build() {
            return new CELogAppender(getName(), getFilter(), getLayout(), isIgnoreExceptions(), null, target);
        }
    }
}
