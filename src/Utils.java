import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.PropertiesCredentials;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import org.apache.commons.codec.binary.Base64;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by malachi on 4/9/16.
 *
 * Common Amazon objects for local app, manager and workers for saving code
 */
class Utils {

    static AmazonEC2 ec2_client;
    static String manager_instanceId;
    static AmazonSQS sqs_client;
    static AmazonS3 s3_client;
    static AWSCredentials credentials;

    // Queue URL format: x_y_queue_url means x-->y direction queue.
    static String local_manager_queue_url;
    static String manager_local_queue_url;
    static String workers_manager_queue_url;
    static String manager_workers_queue_url;

    // Bash files content.
    public static String worker_user_data;
    public static String manager_user_data;


    static void init() throws IOException {
        System.out.println("Init Credentials");
        initCredentials();

        System.out.println("Init S3");
        initS3();

        System.out.println("Init Ec2Client");
        initEC2Client();

        System.out.println("Init SQS");
        initSqs();

        // Load worker and manager data from file.
        worker_user_data = loadFromFile("Resources/worker.sh");
        manager_user_data = loadFromFile("Resources/manager.sh");
    }

    /**
     * Load file data into a string.
     *
     * @param filePath
     * @return file's data.
     * @throws IOException
     */
    private static String loadFromFile(String filePath)  throws IOException {
        String data = "";
        BufferedReader reader = null;
        String currentLine;

        reader = new BufferedReader(new FileReader(filePath));
        while ((currentLine = reader.readLine()) != null) {
            data += currentLine + "\n";
        }
        if (reader != null) {
            reader.close();
        }

        return data;
    }

    /**
     * Initiate credentials from file.
     */
    private static void initCredentials() throws IOException {
        File credentials_file = new File("Resources/AwsCredentials.properties");

        if (!credentials_file.exists()) {
            throw new IOException("No credential file found.");
        }

        credentials = new PropertiesCredentials(credentials_file);
    }

    private static void initS3() {
        s3_client = new AmazonS3Client(credentials);
    }

    private static void initSqs() throws IOException {
        // Create a queue
        sqs_client = new AmazonSQSClient(credentials);

        local_manager_queue_url = getQueue("local_manager_queue");
        manager_local_queue_url = getQueue("manager_local_queue");
        manager_workers_queue_url= getQueue("manager_workers_queue");
        workers_manager_queue_url= getQueue("workers_manager_queue");
//        Utils.clearAllSQS();
//        System.out.println("CLEARED");
    }


    private static void initEC2Client() throws IOException {
        // Set client connection
        ec2_client = new AmazonEC2Client(credentials);
        ec2_client.setEndpoint("ec2.us-west-2.amazonaws.com");
    }

    /**
     * Gets a queue by name. Creates one if does not exist.
     *
     * @param name Name of the queue.
     * @return Queue URL.
     */
    private static String getQueue(String name) {
        try {
            CreateQueueRequest createQueueRequest = new CreateQueueRequest(name);
            return sqs_client.createQueue(createQueueRequest).getQueueUrl();
        }
        catch (Exception e) {
            return sqs_client.getQueueUrl(name).getQueueUrl();
        }
    }

    /**
     *
     * @param tag : tag of machine
     * @param userData : user data for machine
     * @return String instance ID of created machine
     */
    static String createEC2Instance(String tag, String userData) throws UnsupportedEncodingException {
        // Request for booting machine up with key pair kp
        RunInstancesRequest request = new RunInstancesRequest().
                withImageId("ami-c229c0a2").
                withMinCount(1).
                withMaxCount(1).
                withInstanceType(InstanceType.T2Micro).
                withKeyName("kp").
                withSecurityGroupIds("sg-01a8dd66");

        // Base configuration.
        String base64UserData = new String(Base64.encodeBase64(userData.getBytes("UTF-8")), "UTF-8");
        request.setUserData(base64UserData);
        RunInstancesResult runInstancesResult = Utils.ec2_client.runInstances(request);

        // Save ID locally.
        String instancesId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();

        // Tag the remote instance.
        tagInstance(instancesId, "name", tag);

        return instancesId;
    }


    /**
     * Tag a remote instance.
     *
     * @param instanceId the instance to tag
     * @param tag        the tag to give to the instance ( example: "name" )
     * @param value      the value of the tag ( example: "worker" )
     */
    public static void tagInstance(String instanceId, String tag, String value) {
        CreateTagsRequest request = new CreateTagsRequest();
        request = request.withResources(instanceId)
                .withTags(new Tag(tag, value));
        Utils.ec2_client.createTags(request);
    }


    /**
     * Clear queue for debugging.
     */
    public static void clearSQS(String queueUrl){
        sqs_client.purgeQueue(new PurgeQueueRequest(queueUrl));

    }

    /**
     * Debug only; Clear all queues.
     */
    public static void clearAllSQS() {
        Utils.clearSQS(Utils.manager_local_queue_url);
        Utils.clearSQS(Utils.local_manager_queue_url);
        Utils.clearSQS(Utils.manager_workers_queue_url);
        Utils.clearSQS(Utils.workers_manager_queue_url);
        System.out.println("ALL SQS CLEARED");
    }

    /**
     * Create a new manager instance.
     * @return manager instance.
     */
    public static String createManager() throws UnsupportedEncodingException {
        return createEC2Instance("manager", manager_user_data);
    }

    /**
     * Turns the results to an HTML string.
     *
     * @param results
     *  ArrayList<String> of results from workers.
     * @return
     *  HTML-encoded string.
     */
    public static String resultsToHtml(ArrayList<String> results) {
        String data = "";
        for(String result : results) {
            // Split the result string. Structure:
            // 0 - Key.
            // 1 - Sentiment (1-5).
            // 2 - Entities.
            // 3 - Tweet.
            String[] result_data = result.split("\\|");


            data += "<p><div class=\"sentiment-level-" +
                    result_data[1] +
                    "\">" + result_data[3] + "</div>" +
                    result_data[2] + "</p><br/>";
        }

        return "<html><head>" +
                "<style type=\"text/css\">" +
                ".sentiment-level-1 { color: darkred; }" +
                ".sentiment-level-2 { color: red; }" +
                ".sentiment-level-3 { color: black; }" +
                ".sentiment-level-4 { color: lightgreen; }" +
                ".sentiment-level-5 { color: darkgreen; }" +
                "</style></head><body>" +
                data +
                "</body></html>";
    }
}
