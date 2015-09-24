package io.consonance.arch.utils;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import io.consonance.arch.persistence.PostgreSQL;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalINIConfiguration;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.mortbay.log.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * A kitchen sink of utility methods.
 *
 * @author boconnor
 */
public class Utilities {

    protected static final Logger LOG = LoggerFactory.getLogger(Utilities.class);
    // TODO: These really should be refactored out to an enum
    // message types
    public static final String JOB_MESSAGE_TYPE = "job-message-type";

    public static JSONObject parseJSONStr(String jsonStr) {
        JSONObject data;
        JSONParser parser = new JSONParser();
        try {
            data = (JSONObject) parser.parse(jsonStr);
        } catch (ParseException ex) {
            throw new RuntimeException(ex);
        }

        return data;
    }

    public static HierarchicalINIConfiguration parseConfig(String path) {
        try {
            return new HierarchicalINIConfiguration(path);
        } catch (ConfigurationException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Clears database state and known queues for testing.
     *
     * @param settings consonance config file
     * @throws IOException
     * @throws java.util.concurrent.TimeoutException
     */
    public static void clearState(HierarchicalINIConfiguration settings) throws IOException, TimeoutException {
        File configFile = FileUtils.getFile("src", "test", "resources", "config");
        HierarchicalINIConfiguration parseConfig = Utilities.parseConfig(configFile.getAbsolutePath());
        PostgreSQL postgres = new PostgreSQL(parseConfig);
        // clean up the database
        postgres.clearDatabase();

        String server = settings.getString(Constants.RABBIT_HOST);
        String user = settings.getString(Constants.RABBIT_USERNAME);
        String pass = settings.getString(Constants.RABBIT_PASSWORD);

        Channel channel;

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(server);
        factory.setUsername(user);
        factory.setPassword(pass);
        factory.setAutomaticRecoveryEnabled(true);
        Connection connection = factory.newConnection();
        channel = connection.createChannel();
        channel.basicQos(1);
        channel.confirmSelect();

        String prefix = settings.getString(Constants.RABBIT_QUEUE_NAME);
        String[] queues = { prefix + "_jobs", prefix + "_orders", prefix + "_vms", prefix + "_for_CleanupJobs", prefix + "_for_CleanupVMs" };
        for (String queue : queues) {
            try {
                channel.queueDelete(queue);
            } catch (IOException e) {
                Log.info("Could not delete " + queue);
            }
        }
    }

    /**
     * Setup a queue
     * @param settings consonance config file
     * @param queue name of queue to setup
     * @return channel for the queue
     * @throws IOException
     * @throws TimeoutException
     */
    public static Channel setupQueue(HierarchicalINIConfiguration settings, String queue) throws IOException, TimeoutException {

        String server = settings.getString(Constants.RABBIT_HOST);
        String user = settings.getString(Constants.RABBIT_USERNAME);
        String pass = settings.getString(Constants.RABBIT_PASSWORD);

        Channel channel = null;

        try {

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(server);
            factory.setUsername(user);
            factory.setPassword(pass);
            factory.setAutomaticRecoveryEnabled(true);
            Connection connection = factory.newConnection();
            channel = connection.createChannel();
            channel.basicQos(1);
            channel.queueDeclare(queue, true, false, false, null);
            channel.confirmSelect();
            // channel.queueDeclarePassive(queue);

        } catch (IOException | TimeoutException ex) {
            // Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
            LOG.error("Error setting up queue connections to queue:" + queue + " on host: " + server + "; error is: " + ex.getMessage(), ex);
        }
        return channel;

    }

    /**
     * Setup an exchange
     * @param settings consonance config file
     * @param exchange name of the exchange
     * @return a reference to the channel for the exchange
     * @throws IOException
     * @throws TimeoutException
     */
    public static Channel setupExchange(HierarchicalINIConfiguration settings, String exchange) throws IOException, TimeoutException {
        return setupExchange(settings, exchange, "fanout");
    }

    /**
     * Setup an exchange
     * @param settings consonance config file
     * @param exchange name of the exchange
     * @param exchangeType type of the exchange, looks like it can be direct or fanout, not sure if there are more
     * @return a reference to the channel for the exchange
     * @throws IOException
     * @throws TimeoutException
     */
    public static Channel setupExchange(HierarchicalINIConfiguration settings, String exchange, String exchangeType) throws IOException, TimeoutException {

        String server = settings.getString(Constants.RABBIT_HOST);
        String user = settings.getString(Constants.RABBIT_USERNAME);
        String pass = settings.getString(Constants.RABBIT_PASSWORD);

        Channel channel;

        try {

            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(server);
            factory.setUsername(user);
            factory.setPassword(pass);
            factory.setAutomaticRecoveryEnabled(true);
            Connection connection = factory.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclare(exchange, exchangeType, true, false, null);
            channel.confirmSelect();

        } catch (IOException | TimeoutException ex) {
            // Logger.getLogger(Master.class.getName()).log(Level.SEVERE, null, ex);
            LOG.error("Error setting up exchange connections: " + ex.getMessage(), ex);
            throw ex;
        }
        return channel;
    }

    public static String setupQueueOnExchange(Channel channel, String queue, String suffix) throws IOException {
        try {
            return channel.queueDeclare(queue + "_for_" + suffix, true, false, false, null).getQueue();
        } catch (IOException ex) {
            LOG.error("Error setting up queue on exchange: " + ex.getMessage(), ex);
            throw ex;
        }
    }

    public JSONObject parseJob(String job) {
        return parseJSONStr(job);
    }


    /**
     *
     * @param min
     *            The (included) lower bound of the range
     * @param max
     *            The (included) upper bound of the range
     *
     * @return The random value in the range
     */
    public static int randInRangeInc(int min, int max) {
        return min + (int) (Math.random() * ((1 + max) - min));
    }

}
