package org.pgmx.cloudpoc.poc3.bolt;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.apache.storm.topology.IRichBolt;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Tuple;
import org.apache.storm.tuple.Values;

import java.util.Map;


public class FieldReducerBolt implements IRichBolt {
    private OutputCollector collector;
    //Fields outFields = null;
    private static final Logger LOG = Logger.getLogger(FieldReducerBolt.class);

    @Override
    public void prepare(Map stormConf, TopologyContext context,
                        OutputCollector collector) {
        this.collector = collector;
    }

    @Override
    public void execute(Tuple input) {
        try {

            String inputRecord = input.getString(0);
            String[] inpArr = inputRecord.split(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)");
            inpArr = StringUtils.stripAll(inpArr, "\"");

            // if (LOG.isDebugEnabled()) {
            //    LOG.debug(String.format("#### %s|%s|%s|%s|%s", inpArr[0], inpArr[4], inpArr[5], inpArr[11], inpArr[17]));
            //}

            // Strip header
            if (inpArr[0].equals("Year")) {
                collector.ack(input);
                return;
            }

            // Emit the relevant fields, as a single field (KafkaBolt needs single field)
            // 0,5,23,4,6,10,11,17,25,34,36,41
            String output = String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s", inpArr[0], inpArr[5], inpArr[23],
                    inpArr[4], inpArr[6], inpArr[10], inpArr[11], inpArr[17], inpArr[25], inpArr[34], inpArr[36],
                    inpArr[41]);
            collector.emit(new Values(new Object[]{output}));
            collector.ack(input);

        } catch (Exception aex) {
            //if (LOG.isErrorEnabled()) {
            LOG.error("Ignoring tuple" + input);
            //}
            collector.ack(input);
        }
    }


    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(new Fields(new String[]{"output"}));

        // KafkaBolt needs only one (key) field, so the below does not work!!
        //declarer.declare(new Fields("Year", "FlightDate", "CRSDepTime",
        //        "DayOfWeek", "UniqueCarrier", "FlightNum", "Origin",
        //        "Dest", "DepDelay", "CRSArrTime", "ArrDelay",
        //        "Cancelled"));
    }

    @Override
    public void cleanup() {
        //if (LOG.isInfoEnabled()) {
        LOG.info("-------------- FieldReducerBolt exit ---------------");
        //}
    }

    @Override
    public Map<String, Object> getComponentConfiguration() {
        return null;
    }


    public static void main(String s[]) throws Exception {

        String csString = "a,b,c,\"d\",\"e,f\"";
        String[] splitted = csString.split(",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)");
        splitted = StringUtils.stripAll(splitted, "\"");
        for (String str : splitted) {
            System.out.println(str);
        }
    }
}