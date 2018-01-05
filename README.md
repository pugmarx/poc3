# POC2: Kafka -> Storm -> Cassandra flow
Demonstrates filtering on Streaming data simulated via a Kafka topic.
Sample data used is [Airline Ontime](https://www.transtats.bts.gov/Tables.asp?DB_ID=120&DB_Name=Airline%20On-Time%20Performance%20Data&DB_Short_Name=On-Time) data. The (raw) data is filtered by Storm, and the cleaned-up data is routed to 2 destinations:
1. Another Kafka Topic (for further processing)
2. Cassandra (dump)

### Prerequisites
1. Kafka running on localhost with a topic named `raw` and `proc`
2. Cassandra running on localhost with keyspace `cloudpoc`

### Building
`mvn package -DskipTests=true`

### Running
`{project-dir}$ storm jar target/poc3-1.1.1.jar org.pgmx.cloudpoc.poc3.topology.KafkaLocalReaderTopology`