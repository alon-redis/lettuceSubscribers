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

