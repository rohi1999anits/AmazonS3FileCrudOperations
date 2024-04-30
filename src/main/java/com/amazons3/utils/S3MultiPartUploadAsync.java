package com.amazons3.utils;

import com.amazonaws.regions.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.internal.Constants;
import com.amazonaws.services.s3.model.*;

import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Class for saving InputStream to S3 Bucket without Content-Length using Multipart Upload.
 * <p>
 * Links:
 * https://docs.aws.amazon.com/AmazonS3/latest/dev/mpuoverview.html
 * https://docs.aws.amazon.com/AmazonS3/latest/dev/qfacts.html
 * <p>
 * Example:
 * https://docs.aws.amazon.com/AmazonS3/latest/dev/llJavaUploadFile.html
 *
 */
@Component
public class S3MultiPartUploadAsync {
	private static final Logger log = LoggerFactory.getLogger(S3MultiPartUploadAsync.class);

    private static final int AWAIT_TIME = 2; // in seconds
    private static final int DEFAULT_THREAD_COUNT = 4;

    private String destBucketName;
    private String filename;

    private ThreadPoolExecutor executorService;
    
    @Autowired
    private AmazonS3 s3Client ;

    private String uploadId;

    // uploadPartId should be between 1 to 10000 inclusively
    private final AtomicInteger uploadPartId = new AtomicInteger(0);

    private final List<Future<PartETag>> futuresPartETags = new ArrayList<>();
    
	
	  @PostConstruct 
	  public void initialize() { 
		  this.executorService =(ThreadPoolExecutor) Executors.newFixedThreadPool(DEFAULT_THREAD_COUNT); 
		  }
	 
    
    public void setMandateFileds(String bucketName, String fileName){
    	this.destBucketName = bucketName;
    	this.filename = fileName;
    }

    /**
     * We need to call initialize upload method before calling any upload part.
     */
    public boolean initializeUpload() {
    	/**
    	 * AWS provides an id to identify this process for the next steps — uploadId. 
    	 * We also get an abortRuleIdin case we decide to not finish this multipart upload, 
    	 * possibly due to an error in the following steps
    	 */

        InitiateMultipartUploadRequest initRequest = new InitiateMultipartUploadRequest(destBucketName, filename);
        initRequest.setObjectMetadata(getObjectMetadata()); // if we want to set object metadata in S3 bucket
        initRequest.setTagging(getObjectTagging()); // if we want to set object tags in S3 bucket

        uploadId = s3Client.initiateMultipartUpload(initRequest).getUploadId();

        return false;
    }

    public void uploadPartAsync(ByteArrayInputStream inputStream) {
        submitTaskForUploading(inputStream, false);
    }

    public void uploadFinalPartAsync(ByteArrayInputStream inputStream) {
        try {
            submitTaskForUploading(inputStream, true);

            // wait and get all PartETags from ExecutorService and submit it in CompleteMultipartUploadRequest
            List<PartETag> partETags = new ArrayList<>();
            for (Future<PartETag> partETagFuture : futuresPartETags) {
                partETags.add(partETagFuture.get());
            }

            // Complete the multipart upload
            CompleteMultipartUploadRequest completeRequest = new CompleteMultipartUploadRequest(destBucketName, filename, uploadId, partETags);
            s3Client.completeMultipartUpload(completeRequest);

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            // finally close the executor service
            this.shutdownAndAwaitTermination();
        }

    }

    /**
     * This method is used to shutdown executor service
     */
    private void shutdownAndAwaitTermination() {
        log.debug("executor service await and shutdown");
        this.executorService.shutdown();
        try {
            this.executorService.awaitTermination(AWAIT_TIME, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.debug("Interrupted while awaiting ThreadPoolExecutor to shutdown");
        }
        this.executorService.shutdownNow();
    }

    private void submitTaskForUploading(ByteArrayInputStream inputStream, boolean isFinalPart) {
        if (uploadId == null || uploadId.isEmpty()) {
            throw new IllegalStateException("Initial Multipart Upload Request has not been set.");
        }

        if (destBucketName == null || destBucketName.isEmpty()) {
            throw new IllegalStateException("Destination bucket has not been set.");
        }

        if (filename == null || filename.isEmpty()) {
            throw new IllegalStateException("Uploading file name has not been set.");
        }

        submitTaskToExecutorService(() -> {
            int eachPartId = uploadPartId.incrementAndGet();
            UploadPartRequest uploadRequest = new UploadPartRequest()
                    .withBucketName(destBucketName)
                    .withKey(filename)
                    .withUploadId(uploadId)
                    .withPartNumber(eachPartId) // partNumber should be between 1 and 10000 inclusively
                    .withPartSize(inputStream.available())
                    .withInputStream(inputStream);

            if (isFinalPart) {
                uploadRequest.withLastPart(true);
            }

            log.info(String.format("Submitting uploadPartId: %d of partSize: %d", eachPartId, inputStream.available()));

            UploadPartResult uploadResult = s3Client.uploadPart(uploadRequest);

            log.info(String.format("Successfully submitted uploadPartId: %d", eachPartId));
/**
 * Once a part upload request is formed, the output stream is cleared 
 * so that there is no overlap with the next part. 
 * We also track the part number and the ETag response for the multipart upload. 
 * We will need them in the next step. ETag is in most cases the MD5 Hash of the 
 * object, which in our case would be a single part object.
 */
            return uploadResult.getPartETag();
        });
    }

    private void submitTaskToExecutorService(Callable<PartETag> callable) {
        // we are submitting each part in executor service and it does not matter which part gets upload first
        // because in each part we have assigned PartNumber from "uploadPartId.incrementAndGet()"
        // and S3 will accumulate file by using PartNumber order after CompleteMultipartUploadRequest
        Future<PartETag> partETagFuture = this.executorService.submit(callable);
        this.futuresPartETags.add(partETagFuture);
    }

    private ObjectTagging getObjectTagging() {
        // create tags list for uploading file
        return new ObjectTagging(new ArrayList<>());
    }

    private ObjectMetadata getObjectMetadata() {
        // create metadata for uploading file
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType("application/zip");
        return objectMetadata;
    }
}