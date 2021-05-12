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
    private final ConcurrentMap<String, LogEvent> eventMap = new ConcurrentHashMap<>();
    private final String targetDbPath;
    private Database targetDb;
    private Document logDoc;
    private MIMEEntity mimeEntity;
    private Stream stream;
    private long warningCount = 0;
    private long errorCount = 0;

    protected CELogAppender(String name, Filter filter, Layout<? extends Serializable> layout,
                            boolean ignoreExceptions, Property[] properties, String target) {
        super(name, filter, layout, ignoreExceptions, properties);
        targetDbPath = target;
    }

    @PluginBuilderFactory
    public static <B extends Builder<B>> B newBuilder() {
        return new Builder<B>().asBuilder();
    }

    @Override
    public void append(LogEvent event) {
        eventMap.put(Instant.now().toString(), event);
        byte[] content = getLayout().toByteArray(event);
        String message = new String(content, StandardCharsets.UTF_8);

        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family: Arial; font-size: 10px; margin-left: 5px; margin-right: 5px\">");
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

        Level level = event.getLevel();
        if (Level.ERROR.equals(level)) {
            errorCount++;
        } else if (Level.FATAL.equals(level)) {
            errorCount++;
        } else if (Level.WARN.equals(level)) {
            warningCount++;
        }
    }

    public ConcurrentMap<String, LogEvent> getEvents() {
        return eventMap;
    }

    @Override
    public void start() {
        super.start();
        try {
            NotesThread.sinitThread();
            Session session = NotesFactory.createSession();
            String[] split = targetDbPath.split("!!");
            String server = split[0];
            String path = split[1];
            targetDb = session.getDatabase(server, path, false);
            if (!targetDb.isOpen()) {
                targetDb.open();
            }

            logDoc = targetDb.createDocument();

            logDoc.replaceItemValue("Form", "faCELog");
            logDoc.replaceItemValue("fdLogDate", session.createDateTime(new Date()));
            logDoc.replaceItemValue("fdLogEvent", getName());
            logDoc.replaceItemValue("fdUser", session.getEffectiveUserName());
            logDoc.replaceItemValue("fdLogNumber", 1);
            logDoc.replaceItemValue("fdDBName", targetDb.getTitle());

            mimeEntity = logDoc.createMIMEEntity("fdLogEntrys");
            stream = session.createStream();
        } catch (NotesException e) {
            throw new LoggingException(e);
        }
    }

    @Override
    public boolean stop(long timeout, TimeUnit timeUnit) {
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
