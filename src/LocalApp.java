import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class LocalApp {
    private String input_file_name;
    private String output_file_name;
    private int num_tasks_per_worker;


    private LocalApp(String input_file_name, String output_file_name, String num_file_per_worker) throws IOException {
        Utils.init();

        this.input_file_name = input_file_name;
        this.output_file_name =  output_file_name;
        this.num_tasks_per_worker = Integer.parseInt(num_file_per_worker);
    }

    /**
     * Start local App
     *
     * @throws IOException
     * @throws InterruptedException
     */
    private void startLocalApp() throws IOException, InterruptedException {
        //  Checks if a Manager node is active on the EC2 cloud. If it is not, the application will start the manager node.
        System.out.println("Getting manager...");
        Utils.manager_instanceId = getManager();


        if (Utils.manager_instanceId == null) {
            System.out.println("Manager is down, Creating one");
            // upload manager jar file to s3_client
            System.out.println("Uploading jars");
            uploadJars();

            // start manager
            System.out.println("Starting manager instances");
            Utils.manager_instanceId = Utils.createManager();
        }

        //  Uploads the file to S3.
        System.out.println("uploading file to S3...");
        String key = uploadFileToStorage();

        //  Sends a message to an SQS queue, stating the location of the file on S3
        acknowledgeFileLocation(key);

        //  Checks an SQS queue for a message indicating the process is done and the response (the summary file) is available on S3.
        waitForDone(key);

        //  Downloads the summary file from S3, and create an html file representing the results.
        ArrayList<String> lines = downloadSummary(key);

        BufferedWriter output = null;
        try {
            File file = new File(output_file_name);
            if (file.exists()) {
                // Clean out old file.
                file.delete();
            }
            output = new BufferedWriter(new FileWriter(file));
            output.write(Utils.resultsToHtml(lines));
        }
        catch ( IOException e ) {
            e.printStackTrace();
        }
        finally {
            if ( output != null ) {
                output.close();
            }
        }
        // Sends a termination message to the Manager if it was supplied as one of its input arguments.
        sendTerminationToManager();
    }


    /**
     * upload to S3 the manager jar to the bucket "malachi-amir-bucket"
     * for the manager EC2 node will be able to download it from there
     */
    private void uploadJars() {
        String bucket_name = "malachi-amir-bucket";

        try {
            Utils.s3_client.createBucket(bucket_name);
        } catch (Exception e) {
            System.out.println("Error creating bucket : " + e.toString());
        }

        putJar("Resources/Worker.jar", "worker.jar");
        putJar("Resources/Manager.jar", "manager.jar");
    }

    private void putJar(String path, String key) {
        // Put object in bucket request
        File jar_file = new File(path);
        PutObjectRequest req = new PutObjectRequest("malachi-amir-bucket", key, jar_file);

        // Set permission so everyone can download object, so the manager wil be able to download the object
        req.setCannedAcl(CannedAccessControlList.PublicRead);
        Utils.s3_client.putObject(req);
    }


    /**
     * Check if manager instance if up by checking the machines tag
     *
     * @return manager instance ID if up, else null.
     */
    private String getManager() throws IOException {
        DescribeInstancesRequest request = new DescribeInstancesRequest();
        DescribeInstancesResult result = Utils.ec2_client.describeInstances(request);

        List<Reservation> reservations = result.getReservations();

        for (Reservation reservation : reservations) {
            List<Instance> instances = reservation.getInstances();
            for (Instance instance : instances) {
                if ((instance.getState().getCode() == 0) || (instance.getState().getCode() == 16)) { //  0 pending or  16 running
                    List<Tag> tags = instance.getTags();
                    for (Tag tag : tags) {
                        if (tag.getKey().equals("name") && tag.getValue().equals("manager"))
                            return instance.getInstanceId();
                    }
                }
            }
        }
        return null;
    }

    /**
     * create bucket (if not exists) named "bucket".
     * upload the tweet file to storage
     *
     * @return key of the file.
     * @throws IOException
     */
    private String uploadFileToStorage() throws IOException {
        // Create bucket : we have only one bucket, named "malachi-amir-bucket", if exists: log it
        String bucket_name = "malachi-amir-bucket";
        try {
            Utils.s3_client.createBucket(bucket_name);
        } catch (Exception e) {
            System.out.println("Bucket already exists..");
        }

        // The key is the filename within the bucket
        String key = null;

        System.out.println("Uploading tweets file to S3...\n");

        // Directory contains files to upload
        // TODO: change path to be relative
        String directoryName = "Resources/uploads";
        File dir = new File(directoryName);

        // Key is the identifier of the task
        for (File file : dir.listFiles()) {
            if(!file.getName().equals(this.input_file_name)) {
                continue;
            }
            // Generate random num, for unique key
            Random rand = new Random();
            key = rand.nextInt(Integer.MAX_VALUE) + "";

            // Put file in bucket
            PutObjectRequest req = new PutObjectRequest(bucket_name, key, file);
            Utils.s3_client.putObject(req);
        }
        return key;
    }

    /**
     * Put the key inside the queue, so the manager will know what key should he download from s3 malachi-amir-bucket
     * take the message, and ask the file "@key" from the bucket "malachi-amir-bucket" in s3_client
     *
     * @param key the tweets file key
     */
    private void acknowledgeFileLocation(String key) {
        Utils.sqs_client.sendMessage(new SendMessageRequest(Utils.local_manager_queue_url, key));
    }


    /**
     * When we will see a message in the answers queue named "@key", then we will know that
     * the manager done parsing
     *
     * @param key
     * @throws InterruptedException
     */
    private void waitForDone(String key) throws InterruptedException {
        System.out.println("Receiving messages from answers queue.\n");

        while (true) {
            // Get messages from queue request
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(Utils.manager_local_queue_url);
            // When pulling item from queue, other machines can't see it for some time. we don't want it here.
            // So change this timeout to 0, so everyone can see everything anytime
            receiveMessageRequest.setVisibilityTimeout(0);

            // Get 10 oldest messages, in case a machine fall, so all the others won't be stuck
            receiveMessageRequest.setMaxNumberOfMessages(10);
            receiveMessageRequest.getMaxNumberOfMessages();

            // Get messages
            List<Message> messages = Utils.sqs_client.receiveMessage(receiveMessageRequest).getMessages();
            // For each message in queue
            for (Message message : messages) {
                // if its name equals to "@key"
                if (message.getBody().equals(key+"|DONE")) {
                    // Delete message from queue and return.
                    Utils.sqs_client.deleteMessage(new DeleteMessageRequest(Utils.manager_local_queue_url, message.getReceiptHandle()));
                    return;
                }
            }
            Thread.sleep(1000);
        }
    }

    /**
     * After the manager send done message, then we can download the summary file from S3
     */
    private ArrayList<String> downloadSummary(String key) throws IOException {
        S3Object s3object = Utils.s3_client.getObject(new GetObjectRequest("malachi-amir-bucket", key));
        BufferedReader reader = new BufferedReader(new InputStreamReader(s3object.getObjectContent()));

        ArrayList<String> lines = new ArrayList<String>();

        String line;

        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }

        return lines;

    }

    /**
     * Send a termination signal to the remote manager.
     */
    private void sendTerminationToManager() {
        Utils.sqs_client.sendMessage(new SendMessageRequest(Utils.local_manager_queue_url, "TERMINATE"));
    }

    /**
     * Localapp executable.
     *
     * @param args Arguments:
     * 0 - String Input file name.
     * 1 - String output file name.
     * 2 - Int Files per worker.
     * @throws IOException
     * @throws InterruptedException
     */

    public static void main(String[] args) throws IOException, InterruptedException {

        String input_file_name = args[0];
        String output_file_name = args[1];
        String num_files_per_worker = args[2];

        // Create local app.
        LocalApp local_app = new LocalApp(input_file_name, output_file_name, num_files_per_worker);

        // Start local app.
        local_app.startLocalApp();
    }
}
