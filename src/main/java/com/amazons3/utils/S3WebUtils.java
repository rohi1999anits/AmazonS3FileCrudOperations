package com.amazons3.utils;

import java.io.File;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.HttpMethod;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;

@Component
public class S3WebUtils {
	
	//Here the S3 client Bean will be automatically Autowired from S3Config due to @Bean 
	
	@Autowired
    private AmazonS3 s3Client ;
	
	private static final Logger LOGGER = LoggerFactory.getLogger(S3WebUtils.class);
	
	/**
     * Uploading a file to S3 Bucket using S3 PutObjectRequest using s3Client will call that Put api in the background
     */
	public void uploadFileToS3(String bucketName, File file){
    	final String uniqueFileName = LocalDateTime.now() + "_" + file.getName();
        LOGGER.info("Uploading file with name= " + uniqueFileName);
    	PutObjectRequest putObject = new PutObjectRequest(bucketName, uniqueFileName, file);
    	//Inside static methods can access only static members
    	s3Client.putObject(putObject);	
    }
	
	public String generatePreSignedUrl(String bucketName, File file) {
		//Set the PreSignedUrl Expire after 1 hour
		Date expiration = new Date();
		long expTimeMillis = expiration.getTime();
		expTimeMillis+=1000*60*60;
		expiration.setTime(expTimeMillis);
		//Object Key is also known as filePath where Object will be stored under specified key in the specified bucket
		String objectKey = "/usertenant/TestS3FileUpload.txt";
		 GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectKey)
                 .withMethod(HttpMethod.PUT)
                 .withExpiration(expiration);
		 URL url =s3Client.generatePresignedUrl(generatePresignedUrlRequest);
		 return url.toString();
	}

}
