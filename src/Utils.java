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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

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

    // queue urls: x_y_queue_url means x-->y direction queue
    static String local_manager_queue_url;
    static String manager_local_queue_url;
    static String workers_manager_queue_url;
    static String manager_workers_queue_url;

    public static String worker_user_data;

    public static String manager_user_data;



    static void init() throws IOException {
        System.out.println("init_credentials");
        init_credentials();

        System.out.println("init_s3");
        init_s3();

        System.out.println("init_ec2_client");
        init_ec2_client();

        System.out.println("init_sqs");
        init_sqs();

        worker_user_data = "#!/bin/bash" + "\n";
        worker_user_data += "wget http://repo1.maven.org/maven2/edu/stanford/nlp/stanford-corenlp/3.3.0/stanford-corenlp-3.3.0-models.jar && ";
        worker_user_data += "wget http://repo1.maven.org/maven2/com/googlecode/efficient-java-matrix-library/ejml/0.23/ejml-0.23.jar && ";
        worker_user_data += "wget http://repo1.maven.org/maven2/edu/stanford/nlp/stanford-corenlp/3.3.0/stanford-corenlp-3.3.0.jar && ";
        worker_user_data += "wget http://garr.dl.sourceforge.net/project/jollyday/releases/0.4.7/jollyday-0.4.7.jar && ";
        worker_user_data += "wget http://malachi-amir-bucket.s3.amazonaws.com/worker.jar && java -jar worker.jar 5 >> worker_log.txt";
        worker_user_data += "java -cp .:worker.jar:stanford-corenlp-3.3.0.jar:stanford-corenlp-3.3.0-models.jar:ejml-0.23.jar:jollyday-0.4.7.jar -jar worker.jar";

        manager_user_data = "#!/bin/bash" + "\n";
        manager_user_data += "wget http://malachi-amir-bucket.s3.amazonaws.com/manager.jar && java -jar manager.jar 5 >> manager_log.txt";
    }

    /**
     * Initiate credentials from file.
     * @throws IOException
     */
    private static void init_credentials() throws IOException {
        File credentials_file = new File("Resources/AwsCredentials.properties");

        if (!credentials_file.exists()) {
            throw new IOException("No credential file found.");
        }

        credentials = new PropertiesCredentials(credentials_file);
    }

    private static void init_s3() {
        s3_client = new AmazonS3Client(credentials);
    }

    private static void init_sqs() throws IOException {
        // Create a queue
        sqs_client = new AmazonSQSClient(credentials);

        local_manager_queue_url = create_queue("local_manager_queue");
        manager_local_queue_url = create_queue("manager_local_queue");
        manager_workers_queue_url= create_queue("manager_workers_queue");
        workers_manager_queue_url= create_queue("workers_manager_queue");
//        Utils.clearAllSQS();
//        System.out.println("CLEARED");
    }


    private static void init_ec2_client() throws IOException {
        // Set client connection
        ec2_client = new AmazonEC2Client(credentials);
        ec2_client.setEndpoint("ec2.us-west-2.amazonaws.com");
    }

    /**
     * create queue named give param name, if already exists, return the existing queue.
     * @param name
     * @return queue url named @name
     */
    private static String create_queue(String name) {
        try {
            CreateQueueRequest createQueueRequest = new CreateQueueRequest(name);
            return sqs_client.createQueue(createQueueRequest).getQueueUrl();
        } catch (Exception e) {
            return sqs_client.getQueueUrl(name).getQueueUrl();
        }
    }


    /**
     *
     * @param tag : tag of machine
     * @param userData : user data for machine
     * @return String instance ID of created machine
     */
    static String createEC2Instane(String tag, String userData) throws UnsupportedEncodingException {
        // Request for booting machine up with key pair kp
        RunInstancesRequest request = new RunInstancesRequest().
                withImageId("ami-c229c0a2").
                withMinCount(1).
                withMaxCount(1).
                withInstanceType(InstanceType.T2Micro).
                withKeyName("kp").
                withSecurityGroupIds("sg-01a8dd66");

        // set user data in order to run whatever we want
        String base64UserData = new String(Base64.encodeBase64(userData.getBytes("UTF-8")), "UTF-8");
        request.setUserData(base64UserData);
        RunInstancesResult runInstancesResult = Utils.ec2_client.runInstances(request);

        // get the id of the created instance
        String instancesId = runInstancesResult.getReservation().getInstances().get(0).getInstanceId();

        // Tag the instance
        tagInstance(instancesId, "name", tag);

        return instancesId;
    }

    /**
     * Giving name (=tag) to an instance, in order to know if it is a manager / worker
     *
     * @param instanceId
     * the instance to tag
     *
     * @param tag
     * the tag to give to the instance ( example: "name" )
     *
     * @param value
     * the value of the tag ( example: "worker" )
     */
    //
    public static void tagInstance(String instanceId, String tag, String value) {
        CreateTagsRequest request = new CreateTagsRequest();
        request = request.withResources(instanceId)
                .withTags(new Tag(tag, value));
        Utils.ec2_client.createTags(request);
    }

    /**
     * Clear queue for debugging..
     */
    public static void clearSQS(String queueUrl){
        sqs_client.purgeQueue(new PurgeQueueRequest(queueUrl));

    }

    public static void clearAllSQS(){
        Utils.clearSQS(Utils.manager_local_queue_url);
        Utils.clearSQS(Utils.local_manager_queue_url);
        Utils.clearSQS(Utils.manager_workers_queue_url);
        Utils.clearSQS(Utils.workers_manager_queue_url);
    }

    public static String createWorker() throws UnsupportedEncodingException {
        return createEC2Instane("worker", worker_user_data);
    }

    public static String createManager() throws UnsupportedEncodingException {
        return createEC2Instane("manager", manager_user_data);
    }

}
