/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jbpm.audit;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.drools.core.WorkingMemory;
import org.drools.core.base.XMLSupport;
import org.drools.kiesession.audit.LogEvent;
import org.drools.kiesession.audit.WorkingMemoryFileLogger;
import org.drools.kiesession.audit.WorkingMemoryLog;
import org.drools.kiesession.audit.WorkingMemoryLogger;
import org.drools.util.IoUtils;
import org.kie.api.event.KieRuntimeEventManager;
import org.kie.api.logger.KieRuntimeLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A logger of events generated by a working memory. It stores its information
 * in a file that can be specified. All the events logged are written to the
 * file when the writeToDisk() method is invoked. The log will contain all the
 * events logged serialized to XML using XStream. Every time a new logger is
 * created, the old event log will be overwritten.
 * 
 * TODO: make this class more scalable, for example - logging to several files
 * if log becomes too large - automatically write updates to file at certain
 * time intervals - ...
 */
public class KogitoWorkingMemoryFileLogger extends WorkingMemoryLogger implements KieRuntimeLogger {

    protected static final transient Logger logger = LoggerFactory.getLogger(WorkingMemoryFileLogger.class);

    public static final int DEFAULT_MAX_EVENTS_IN_MEMORY = 1000;

    private List<LogEvent> events = new ArrayList<>();
    private String fileName = "event";
    private int maxEventsInMemory = DEFAULT_MAX_EVENTS_IN_MEMORY;
    private int nbOfFile = 0;
    private boolean split = true;
    private boolean initialized = false;
    protected boolean terminate = false;

    public KogitoWorkingMemoryFileLogger() {
    }

    /**
     * Creates a new WorkingMemoryFileLogger for the given working memory.
     */
    public KogitoWorkingMemoryFileLogger(final WorkingMemory workingMemory) {
        super(workingMemory);
    }

    public KogitoWorkingMemoryFileLogger(final KieRuntimeEventManager session) {
        super(session);
    }

    @SuppressWarnings("unchecked")
    public void readExternal(ObjectInput in) throws IOException,
            ClassNotFoundException {
        super.readExternal(in);
        events = (List<LogEvent>) in.readObject();
        fileName = (String) in.readObject();
        maxEventsInMemory = in.readInt();
        nbOfFile = in.readInt();
        split = in.readBoolean();
        initialized = in.readBoolean();
        terminate = in.readBoolean();
    }

    public void writeExternal(ObjectOutput out) throws IOException {
        super.writeExternal(out);
        out.writeObject(events);
        out.writeObject(fileName);
        out.writeInt(maxEventsInMemory);
        out.writeInt(nbOfFile);
        out.writeBoolean(split);
        out.writeBoolean(initialized);
        out.writeBoolean(terminate);
    }

    /**
     * Sets the name of the file the events are logged in. No extensions should
     * be given since .log is automatically appended to the file name. The
     * default is an event.log file in the current working directory. This can
     * be a path relative to the current working directory (e.g.
     * "mydir/subDir/myLogFile"), or an absolute path (e.g. "C:/myLogFile").
     *
     * @param fileName
     *        The name of the file the events should be logged in.
     */
    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    /**
     * All events in the log are written to file. The log is automatically
     * cleared afterwards.
     */
    public void writeToDisk() {
        if (!initialized) {
            initializeLog();
        }
        try (FileOutputStream fileOut = new FileOutputStream(this.fileName + (this.nbOfFile == 0 ? ".log" : this.nbOfFile + ".log"), true);
                Writer writer = new OutputStreamWriter(fileOut, IoUtils.UTF8_CHARSET)) {

            WorkingMemoryLog log;
            synchronized (this.events) {
                log = new WorkingMemoryLog(new ArrayList<>(this.events));
                clear();
            }

            writer.write(XMLSupport.get().toXml(log) + "\n");
        } catch (final FileNotFoundException exc) {
            throw new RuntimeException("Could not create the log file.  Please make sure that directory that the log file should be placed in does exist.");
        } catch (final Throwable t) {
            logger.error("error", t);
        }
        if (terminate) {
            closeLog();
            terminate = true;
        } else if (split) {
            closeLog();
            this.nbOfFile++;
            initialized = false;
        }
    }

    private void initializeLog() {
        try (FileOutputStream fileOut = new FileOutputStream(this.fileName + (this.nbOfFile == 0 ? ".log" : this.nbOfFile + ".log"), false);
                Writer writer = new OutputStreamWriter(fileOut, StandardCharsets.UTF_8)) {
            writer.append("<object-stream>\n");
            initialized = true;
        } catch (final FileNotFoundException exc) {
            throw new RuntimeException("Could not create the log file.  Please make sure that directory that the log file should be placed in does exist.");
        } catch (final Throwable t) {
            logger.error("error", t);
        }
    }

    private void closeLog() {
        try (FileOutputStream fileOut = new FileOutputStream(this.fileName + (this.nbOfFile == 0 ? ".log" : this.nbOfFile + ".log"), true);
                Writer writer = new OutputStreamWriter(fileOut, StandardCharsets.UTF_8)) {
            writer.append("</object-stream>\n");
        } catch (final FileNotFoundException exc) {
            throw new RuntimeException("Could not close the log file.  Please make sure that directory that the log file should be placed in does exist.");
        } catch (final Throwable t) {
            logger.error("error", t);
        }
    }

    /**
     * Clears all the events in the log.
     */
    private void clear() {
        synchronized (this.events) {
            this.events.clear();
        }
    }

    /**
     * Sets the maximum number of log events that are allowed in memory. If this
     * number is reached, all events are written to file. The default is 1000.
     *
     * @param maxEventsInMemory
     *        The maximum number of events in memory.
     */
    public void setMaxEventsInMemory(final int maxEventsInMemory) {
        this.maxEventsInMemory = maxEventsInMemory;
    }

    public void logEventCreated(final LogEvent logEvent) {
        synchronized (this.events) {
            this.events.add(logEvent);
            if (this.events.size() > this.maxEventsInMemory) {
                writeToDisk();
            }
        }
    }

    public void setSplit(boolean split) {
        this.split = split;
    }

    public void stop() {
        if (!terminate) {
            terminate = true;
            writeToDisk();
        }
    }

    @Override
    public void close() {
        stop();
    }
}
