import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class LocalApp {
    private String input_file_name;
    private String output_file_name;
    private int num_tasks_per_worker;
    private boolean terminate;

    // File password for jars encryption.
    // Remember to change in encryptFiles.sh as well.
    private static final String FILE_PASSWORD = "foofoofoofoo";

    public static final String BUCKET_NAME = "malachi-amir-bucket";


    private LocalApp(String input_file_name, String output_file_name, String mission_per_worker, boolean terminate) throws IOException {
        Utils.init(mission_per_worker, FILE_PASSWORD);
        this.terminate = terminate;
        this.input_file_name = input_file_name;
        this.output_file_name = output_file_name;
        this.num_tasks_per_worker = Integer.parseInt(mission_per_worker);
    }

    /**
     * upload to S3 the manager jar to the bucket "malachi-amir-bucket"
     * for the manager EC2 node will be able to download it from there
     */
    private void uploadJars() {

        try {
            Utils.s3_client.createBucket(BUCKET_NAME);
        }
        catch (Exception e) {
            System.out.println("Error creating bucket : " + e.toString());
        }

        putJar("Resources/worker.enc", "worker.enc");
        putJar("Resources/manager.enc", "manager.enc");
    }

    private void putJar(String path, String key) {
        // Put object in bucket request
        File jar_file = new File(path);
        if (!jar_file.exists()) {
            System.out.println("Can't find jar file: " + jar_file.getAbsolutePath());
        }

        PutObjectRequest req = new PutObjectRequest(BUCKET_NAME, key, jar_file);

        // Set permission so everyone can download object, so the manager wil be able to download the object.
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
                        if (tag.getKey().equals("Name") && tag.getValue().equals("manager"))
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
        Random randomGenerator = new Random();
        String key = "";

        System.out.println("Uploading files to S3.");

        // Directory contains files to upload
        String directoryName = "Resources/uploads";
        File dir = new File(directoryName);

        // Key is the identifier of the task
        for (File file : dir.listFiles()) {
            String file_name = file.getName();

            if (!file.getName().contains(".sh")) {
                // This is the tweet file.
                key = randomGenerator.nextInt(Integer.MAX_VALUE) + "";
                file_name = key;
            }

            // Put file in bucket.
            PutObjectRequest request = new PutObjectRequest(bucket_name, file_name, file);
            Utils.s3_client.putObject(request);

            System.out.println("Uploaded file: " + file.getName());

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

        ArrayList<String> knownKeys = new ArrayList<String>();

        while (true) {
            // Get messages from queue.
            ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(Utils.manager_local_queue_url);

            // When pulling item from queue, other machines can't see it for some time. we don't want it here.
            // So change this timeout to 0, so everyone can see everything anytime
            receiveMessageRequest.setVisibilityTimeout(0);

            // Get messages
            List<Message> messages = Utils.sqs_client.receiveMessage(receiveMessageRequest).getMessages();
            for (Message message : messages) {
                if (knownKeys.contains(message.getBody())) {
                    // Silently move on.
                    continue;
                }
                System.out.println("Received a message: " + message.getBody());

                if (message.getBody().equals(key + "|DONE")) {
                    // The message says that the task is done.

                    // Delete message from queue and return.
                    Utils.sqs_client.deleteMessage(Utils.manager_local_queue_url, message.getReceiptHandle());

                    System.out.println("Queue is done, starting termination sequence.");
                    return;
                }
                else {
                    System.out.println("Still waiting for done message with ID " + key + ", retrying.");

                    if (message.getBody().contains("|DONE")) {
                        // Avoid repeating messages.
                        knownKeys.add(message.getBody());
                    }
                }
            }

            Thread.sleep(1000);
        }
    }

    /**
     * After the manager send done message, then we can download the summary file from S3
     */
    private ArrayList<String> downloadSummary(String key) throws IOException {
        System.out.println("Downloading summary from bucket.");
        S3Object s3object = Utils.s3_client.getObject(new GetObjectRequest(BUCKET_NAME, key));
        BufferedReader reader = new BufferedReader(new InputStreamReader(s3object.getObjectContent()));

        ArrayList<String> lines = new ArrayList<String>();

        String line;

        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }

        System.out.println("Summary downloaded.");

        // Move the file to a subfolder for neatness.
        System.out.println("Moving summary file.");
        CopyObjectRequest copyObjRequest = new CopyObjectRequest(BUCKET_NAME, key, BUCKET_NAME, "ZZZ_oldSummaries/" + key);
        Utils.s3_client.copyObject(copyObjRequest);
        Utils.s3_client.deleteObject(new DeleteObjectRequest(BUCKET_NAME, key));
        System.out.println("Bucket is tidy :)");

        return lines;
    }

    /**
     * Send a termination signal to the remote manager.
     */
    private void sendTerminationToManager() {
        Utils.sqs_client.sendMessage(new SendMessageRequest(Utils.local_manager_queue_url, "TERMINATE"));
    }

    /**
     * Start local App
     *
     * @param terminate
     * @throws IOException
     * @throws InterruptedException
     */
    private void startLocalApp(boolean terminate) throws IOException, InterruptedException {
        //  Checks if a Manager node is active on the EC2 cloud. If it is not, the application will start the manager node.
        System.out.println("Getting manager.");
        Utils.manager_instanceId = getManager();

        if (Utils.manager_instanceId == null) {
            System.out.println("Manager is down, creating one.");
            // upload manager jar file to s3_client
            System.out.println("Uploading jars.");
//            uploadJars();

            // start manager
            System.out.println("Starting manager instances.");
            Utils.manager_instanceId = Utils.createManager();
        }

        //  Uploads the file to S3.
        String key = uploadFileToStorage();

        //  Sends a message to an SQS queue, stating the location of the file on S3
        //if terminate arg is supplied, then acknowledge the manager
        if (this.terminate) {
            acknowledgeFileLocation("TERMINATE|" + key);
        }
        else {
            acknowledgeFileLocation(key);
        }

        //  Checks an SQS queue for a message indicating the process is done and the response (the summary file) is available on S3.
        waitForDone(key);

        //  Downloads the summary file from S3, and create an HTML file representing the results.
        ArrayList<String> lines = downloadSummary(key);
        Utils.exportToHTMLFile(lines, output_file_name);

        System.out.println("Created HTML file.");

        if (terminate) {
            System.out.println("Got termination signal, waiting for stats file.");
            // Download Stat file from S3
            S3Object s3object = null;
            while (s3object == null) {
                try {
                    s3object = Utils.s3_client.getObject(new GetObjectRequest(BUCKET_NAME, key + "|STATS"));
                }catch (Exception AmazonS3Exception){
                    continue;
                }
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(s3object.getObjectContent()));

            lines.clear();

            String line;

            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
            // Remove file from S3
            Utils.s3_client.deleteObject(BUCKET_NAME, key + "|STATS");

            // Write stats to file
            System.out.println("Writing stats to file");
            Path results_file_path = Paths.get("Stats");
            try {
                Files.write(results_file_path, lines, Charset.forName("UTF-8"));
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Kill manager instance
            System.out.println("Killing manager");
            Utils.ec2_client.terminateInstances(new TerminateInstancesRequest().withInstanceIds(getManager()));
        }
    }

    /**
     * Localapp executable.
     *
     * @param args Arguments:
     *             0 - String Input file name.
     *             1 - String output file name.
     *             2 - Int Files per worker.
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws IOException, InterruptedException {

        String input_file_name = args[0];
        String output_file_name = args[1];
        String missions_per_worker = args[2];

        // Flag which represents if to terminate the app
        boolean terminate = false;

        // Check if termination is supplied as one of the args
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("terminate")) {
                terminate = true;
            }
        }

        // Create local app.
        LocalApp local_app = new LocalApp(input_file_name, output_file_name, missions_per_worker, terminate);

        // Start local app.
        local_app.startLocalApp(terminate);
    }
}
