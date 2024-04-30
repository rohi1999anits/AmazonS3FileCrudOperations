package com.amazons3.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.internal.Constants;
import com.amazons3.service.S3CrudService;

@RestController
@RequestMapping("/crud")
public class S3CrudController {
	
	@Autowired
	S3CrudService s3CrudService;
	
	@PostMapping("/upload")
	public ResponseEntity<String> s3FileUpload(@RequestParam(value="type") String s3UploadType, @RequestParam(value="file") MultipartFile multipartFile){
		final int UPLOAD_LIMIT = 5 * Constants.MB;
		if(multipartFile.getSize() <= UPLOAD_LIMIT) {
		  if("PreSignedURL".equalsIgnoreCase(s3UploadType)) {
		      s3CrudService.uploadS3UsingPreSigned(multipartFile);
		  }
		  else {
		      s3CrudService.uploadFile(multipartFile);
		  }
		}
		else {
			s3CrudService.multipartUploadFile(multipartFile);
		}
		final String response = "[" + multipartFile.getOriginalFilename() + "] uploaded successfully.";
		return new ResponseEntity<>(response, HttpStatus.OK);
		
		
	}
	@GetMapping
	public ResponseEntity<String> test(){
		/*
		 * if("PreSignedURL".equalsIgnoreCase(s3UploadType)) {
		 * 
		 * }
		 */
	
		return new ResponseEntity<>("", HttpStatus.OK);
		
		
	}

}
