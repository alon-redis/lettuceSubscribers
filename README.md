Lettuce install guid on Ubuntu Focal:
https://redislabs.atlassian.net/wiki/spaces/RED/pages/4846944417/Lettuce+-+Advanced+Java+Redis+client


# MultiSubscriber Script

## Overview
The `MultiSubscriber` script is a Java application that uses Lettuce, a Redis client library, to establish Pub/Sub connections to a Redis server. It subscribes to a large number of channels, listens for messages, tracks the rate of messages received per second, and dynamically unsubscribes and resubscribes to channels periodically to simulate dynamic subscription behavior.

## Features
- Subscribes to 20,000 channels across multiple connections.
- Dynamically unsubscribes and resubscribes to five random channels every second.
- Tracks and prints the number of messages received per second.
- Handles connection and resource management gracefully.

## Requirements
- **Redis Server**: The script connects to a Redis server configured with Pub/Sub functionality.
- **Java**: JDK 11 or later.
- **Maven**: For managing dependencies and building the project.

## Setup and Installation

1. **Update Configuration**:
   Modify the following constants in the script if necessary:
   - `REDIS_HOST`: Redis server hostname or IP.
   - `REDIS_PORT`: Redis server port.
   - `TOTAL_CHANNELS`: Number of channels to subscribe to.
   - `CONNECTION_COUNT`: Number of connections to use.

2. **Compile and Run**:
   ```bash
   https://redislabs.atlassian.net/wiki/spaces/RED/pages/4846944417/Lettuce+-+Advanced+Java+Redis+client
   ```

## Usage
- The script subscribes to 20,000 channels and listens for messages.
- Every second, it prints the total number of messages received in that interval.
- It periodically unsubscribes and resubscribes to five random channels to simulate dynamic behavior.

## Output
- **Message Rate**: Printed as the number of messages received per second.

## Example
```bash
100
120
115
...
```

## Error Handling
- Catches and logs errors during unsubscribe/resubscribe operations.
- Ensures all resources (connections and clients) are properly closed on termination.

## Notes
- Ensure the Redis server can handle the load of 20,000 channels and dynamic subscriptions.
- The script can be scaled by adjusting the `CONNECTION_COUNT` and `TOTAL_CHANNELS` variables.



Install instructions:

# Installation Guide: MultiSubscriber for Ubuntu 20.04

## Prerequisites Installation

First, update your system:
```bash
sudo apt update
sudo apt upgrade -y
```

Install OpenJDK 11 (or later):
```bash
sudo apt install openjdk-11-jdk -y
```

Verify Java installation:
```bash
java -version
javac -version
```

Install Maven for building the project:
```bash
sudo apt install maven -y
```

## Project Setup

1. Create a new directory for your project:
```bash
mkdir multi-subscriber
cd multi-subscriber
```

2. Create a Maven project structure:
```bash
mkdir -p src/main/java/com/example
```

3. Create a new `pom.xml` file in the root directory:
```bash
touch pom.xml
```

4. Add the following content to `pom.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>multi-subscriber</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>
        <dependency>
            <groupId>io.lettuce</groupId>
            <artifactId>lettuce-core</artifactId>
            <version>6.2.6.RELEASE</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.4.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.example.MultiSubscriber</mainClass>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

5. Create the MultiSubscriber.java file:
```bash
touch src/main/java/com/example/MultiSubscriber.java
```

6. Copy the MultiSubscriber code provided earlier into this file.

## Building the Application

1. Build the project using Maven:
```bash
mvn clean package
```

This will create a JAR file in the `target` directory named `multi-subscriber-1.0-SNAPSHOT.jar`

## Running the Application

1. The application can be run using the following command:
```bash
java -jar target/multi-subscriber-1.0-SNAPSHOT.jar <redisHost> <redisPort> <connectionCount> <totalChannels>
```

Example:
```bash
java -jar target/multi-subscriber-1.0-SNAPSHOT.jar redis-10006.alon-data2-5160.env0.qa.redislabs.com 10007 10 30000
```

## Troubleshooting

If you encounter any issues:

1. Verify Java installation:
```bash
java -version
```

2. Check Maven installation:
```bash
mvn -version
```

3. Common errors and solutions:

   - **Connection refused**: Verify that the Redis host and port are correct and accessible
   - **OutOfMemoryError**: Add JVM memory parameters:
     ```bash
     java -Xmx4g -jar target/multi-subscriber-1.0-SNAPSHOT.jar <args>
     ```
   - **Permission denied**: Ensure you have write permissions in the directory:
     ```bash
     chmod +x target/multi-subscriber-1.0-SNAPSHOT.jar
     ```

## System Requirements

- Minimum 4GB RAM
- Ubuntu 20.04 LTS
- Java 11 or later
- Maven 3.6 or later
- Stable network connection to Redis server

## Monitoring

The application will output message counts every second. To save the output to a log file:

```bash
java -jar target/multi-subscriber-1.0-SNAPSHOT.jar <args> > output.log 2>&1
```
