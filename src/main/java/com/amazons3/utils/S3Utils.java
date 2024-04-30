package com.amazons3.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public class S3Utils {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(S3Utils.class);
	
	public static File convertMultipartFileToFile(MultipartFile multipartFile) {
    	//Create a file with MulipartFileName
    	File file = new File(multipartFile.getOriginalFilename());
    	//output the contents of multipartFile to file (by reading in bytes) using fileOutputStream
    	//Java 9 try with resource block -(no need to close the fos it automatically closes).
    	try(final FileOutputStream fos = new FileOutputStream(file)){
    		fos.write(multipartFile.getBytes());
    	}
    	catch(IOException io) {
    		LOGGER.error("Error converting the multi-part file to file= ", io.getMessage());
    	}
    	return file;
    }

}
