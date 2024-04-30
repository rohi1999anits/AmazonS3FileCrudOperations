package com.amazons3.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.internal.Constants;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazons3.utils.S3Utils;
import com.amazons3.utils.S3WebUtils;
import com.amazons3.utils.S3MultiPartUploadAsync;

@Service
public class S3CrudService {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(S3CrudService.class);
    
    @Value("${aws.s3.bucket}")
    private String bucketName;
    
    @Autowired
    S3WebUtils s3WebUtils;
    
    @Autowired
    S3MultiPartUploadAsync uploadMultiPartAsync;
    
    public void uploadFile(MultipartFile multipartFile) {
    	LOGGER.info("File upload is Inprogres...");
    	File file = S3Utils.convertMultipartFileToFile(multipartFile);
    	s3WebUtils.uploadFileToS3(bucketName, file);
    	LOGGER.info("File upload is completed.");	
    }
    
    public void uploadS3UsingPreSigned(MultipartFile multipartFile) {
    	LOGGER.info("File upload is Inprogres...");
    	long getTime = System.currentTimeMillis();
    	File file = S3Utils.convertMultipartFileToFile(multipartFile);
    	
    	try {
    	URL url = new URL(s3WebUtils.generatePreSignedUrl(bucketName, file));
    	HttpURLConnection connection = (HttpURLConnection) url.openConnection();
         connection.setDoOutput(true);
         connection.setRequestMethod("PUT");
         try (BufferedInputStream bin = new BufferedInputStream(new FileInputStream(file));
                 ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(connection.getOutputStream())))  
            {
                LOGGER.debug("S3put request built ... sending to s3...");
                
                byte[] readBuffArr = new byte[4096];
                int readBytes = 0;
                while ((readBytes = bin.read(readBuffArr)) >= 0) {
                    out.write(readBuffArr, 0, readBytes);
                }
                connection.getResponseCode();
                LOGGER.debug("response code: {}", connection.getResponseCode());

            } catch (FileNotFoundException e) {
                LOGGER.warn("\tFile Not Found exception");
                LOGGER.warn(e.getMessage());
                e.printStackTrace();
            }
    	} catch (MalformedURLException e) {
            LOGGER.warn(e.getMessage());
            e.printStackTrace();
        } catch (IOException e) {
            LOGGER.warn(e.getMessage());
            e.printStackTrace();
        }
    	LOGGER.info("File upload is completed.");
        getTime = (System.currentTimeMillis() - getTime);
        System.out.print("Total get time in syncCloudMediaAction: {" + getTime + "} milliseconds");
    }
    
    public void multipartUploadFile(MultipartFile multipartFile) {
    	/**
    	 * The limit value defines the minimum byte size we wait for before considering it a valid part
    	 */
    	final int UPLOAD_PART_SIZE = 10 * Constants.MB; // Part Size should not be less than 5 MB while using MultipartUpload
    	File file = S3Utils.convertMultipartFileToFile(multipartFile);
    	uploadMultiPartAsync.setMandateFileds(bucketName, file.getName());
        URL url = null;
        HttpURLConnection connection = null;

        try {
        	uploadMultiPartAsync.initializeUpload();
            int bytesRead, bytesAdded = 0;
            byte[] data = new byte[UPLOAD_PART_SIZE];
            
            InputStream inputStream = new FileInputStream(file);
            ByteArrayOutputStream bufferOutputStream = new ByteArrayOutputStream();

            while ((bytesRead = inputStream.read(data, 0, data.length)) != -1) {
                //To write into byteArray we need bufferOutputStream...
                bufferOutputStream.write(data, 0, bytesRead);

                if (bytesAdded < UPLOAD_PART_SIZE) {
                    // continue writing to same output stream unless it's size gets more than UPLOAD_PART_SIZE
                    bytesAdded += bytesRead;
                    continue;
                } 
               uploadMultiPartAsync.uploadPartAsync(new ByteArrayInputStream(bufferOutputStream.toByteArray()));
                bufferOutputStream.reset(); // flush the bufferOutputStream
                bytesAdded = 0; // reset the bytes added to 0
            }

            // upload remaining part of output stream as final part
            // bufferOutputStream size can be less than 5 MB as it is the last part of upload
            uploadMultiPartAsync.uploadFinalPartAsync(new ByteArrayInputStream(bufferOutputStream.toByteArray()));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
          
        }
    	
    }
}
