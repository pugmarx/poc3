

package org.pgmx.cloudpoc.poc3.topology;


import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.cassandra.bolt.BaseCassandraBolt;
import org.apache.storm.cassandra.bolt.CassandraWriterBolt;
import org.apache.storm.cassandra.client.CassandraConf;
import org.apache.storm.kafka.*;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.bolt.mapper.FieldNameBasedTupleToKafkaMapper;
import org.apache.storm.kafka.bolt.selector.DefaultTopicSelector;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.pgmx.cloudpoc.poc3.bolt.FieldReducerBolt;

import java.util.Properties;
import java.util.UUID;

import static org.apache.storm.cassandra.DynamicStatementBuilder.*;


public class KafkaLocalReaderTopology {
    private static final String ZK_LOCAL_HOST = "localhost:2181";

    private static final String CASSANDRA_KEYSPACE = "cloudpoc";
    private static final String INPUT_TOPIC = "raw";
    private static final String OUTPUT_TOPIC = "proc";
    private static final String BROKER_URL = "localhost:9092";
    private static final String ZK_ROOT = "/brokers";
    private static final String CLIENT_ID = UUID.randomUUID().toString();

    // Storm tuning parameters
    private static final int PARALLELISM_HINT = 5;
    private static final int NUM_TASK_PER_ENTITY = 2;
    private static final int NUM_WORKERS = 1;

    private static final Logger LOG = Logger.getLogger(KafkaLocalReaderTopology.class);

    private static void init(Config config) {
        config.put(CassandraConf.CASSANDRA_NODES, "localhost");
        config.put(CassandraConf.CASSANDRA_PORT, "9042");
        config.put(CassandraConf.CASSANDRA_KEYSPACE, CASSANDRA_KEYSPACE);
    }

    public static void main(String[] args) throws Exception {

        String zkHost = (args.length > 0 && StringUtils.isNotEmpty(args[0])) ? args[0] : ZK_LOCAL_HOST;
        final BrokerHosts ZK_HOSTS = new ZkHosts(zkHost);

        String brokerURL = (args.length > 1 && StringUtils.isNotEmpty(args[1])) ? args[1] : BROKER_URL;
        String inTopic = (args.length > 2 && StringUtils.isNotEmpty(args[2])) ? args[2] : INPUT_TOPIC;
        String outTopic = (args.length > 3 && StringUtils.isNotEmpty(args[3])) ? args[3] : OUTPUT_TOPIC;
        String clientId = (args.length > 4 && StringUtils.isNotEmpty(args[4])) ? args[4] : CLIENT_ID;

        final SpoutConfig kafkaConf = new SpoutConfig(ZK_HOSTS, inTopic, ZK_ROOT, clientId);
        kafkaConf.retryLimit = 0;
        kafkaConf.scheme = new SchemeAsMultiScheme(new StringScheme());

        // Build topology to consume message from kafka and  print them on console
        final TopologyBuilder topologyBuilder = new TopologyBuilder();

        // FIXME - disable debug
        Config config = new Config();
        config.setDebug(false);

        init(config);

        // FIXME experimental
        config.setNumWorkers(NUM_WORKERS);


        // ********************************************************************************
        // ********************* 1. Spout that reads from Kafka ***************************
        // ********************************************************************************
        KafkaSpout kafkaSpout = new KafkaSpout(kafkaConf);
        topologyBuilder.setSpout("kafka-spout", kafkaSpout, PARALLELISM_HINT).setNumTasks(NUM_TASK_PER_ENTITY);


        // ********************************************************************************
        // ********************** 2. Bolt that reads from Spout ***************************
        // ********************************************************************************
        //Route the output of Kafka Spout to Logger bolt to log messages consumed from Kafka
         topologyBuilder.setBolt("reduce-fields", new FieldReducerBolt(), PARALLELISM_HINT)
                 .setNumTasks(NUM_TASK_PER_ENTITY).globalGrouping("kafka-spout");
        //topologyBuilder.setBolt("reduce-fields", new MicroBatchFieldReducerBolt(), PARALLELISM_HINT)
        //        .setNumTasks(NUM_TASK_PER_ENTITY).globalGrouping("kafka-spout");


        // ********************************************************************************
        // ********************* 3. Bolt that persists to Cassandra ***************
        // ********************************************************************************

        // TODO introduce a column to store 'hashes' (PK)
        IRichBolt cassandraBolt = new CassandraWriterBolt(
                async(
                        simpleQuery("INSERT INTO poc3dump (output) VALUES (?);")
                                .with(
                                        fields("output")
                                )
                )
        );


        // Tie the cassandra bolt to reduce-field bolt
        topologyBuilder.setBolt("cas-writer-bolt", cassandraBolt, PARALLELISM_HINT).setNumTasks(NUM_TASK_PER_ENTITY)
                .shuffleGrouping("reduce-fields");

        // ********************************************************************************
        // ********************* 3. Bolt that writes to another Kafka topic ***************
        // ********************************************************************************
        KafkaBolt<String, String> bolt = (new KafkaBolt()).withProducerProperties(newProps(brokerURL,
                outTopic))
                .withTopicSelector(new DefaultTopicSelector(outTopic))
                .withTupleToKafkaMapper(new FieldNameBasedTupleToKafkaMapper("key",
                        "output"));

        // FIXME don't want ACKs
        bolt.setFireAndForget(true);

        // Tie the kafkabolt to reduce-field bolt
        topologyBuilder.setBolt("kafka-producer-bolt", bolt, PARALLELISM_HINT).setNumTasks(NUM_TASK_PER_ENTITY)
                .shuffleGrouping("reduce-fields");

        // Submit topology to local cluster // FIXME cluster?
        final LocalCluster localCluster = new LocalCluster();
        String topologyName = "kafka-local-topology";
        localCluster.submitTopology(topologyName, config, topologyBuilder.createTopology());
    }

    private static Properties newProps(final String brokerUrl, final String topicName) {
        return new Properties() {
            {
                this.put("bootstrap.servers", brokerUrl);
                this.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                this.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
                this.put("client.id", topicName);
            }
        };
    }

}