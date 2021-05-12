# log4j-appender-celog

## Custom log4j appender that writes to a HCL Domino database

### Setup

To work with maven you will have to locally install the Notes.jar file to your local repo

```
mvn install:install-file -Dfile="C:\Program Files (x86)\HCL\Notes\jvm\lib\ext\Notes.jar" -DgroupId="com.ibm" -DartifactId="domino-api-binaries" -Dversion="12" -Dpackaging="jar"
```

### Usage 
Just use it like any other appender, see following example configuration file or have a look at the provided [unit test](https://github.com/sven-olding/log4j-appender-celog/blob/main/src/test/java/com/gi/logging/CELogAppenderTests.java)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration packages="com.gi.logging" status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"/>
        </Console>
        <CELogAppender name="Dokumentendruck-CLI" target="Domino/SOL!!kredit/dokumentendruck.nsf">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} %-5level %logger{36} - %msg%n"/>
        </CELogAppender>
    </Appenders>
    <Loggers>
        <Root level="debug">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="Dokumentendruck-CLI"/>
        </Root>
    </Loggers>
</Configuration>
```
