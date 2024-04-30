package com.amazons3.crud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(
	basePackages = {
			"com.amazons3.controller",
			"com.amazons3.config",
			"com.amazons3.utils",
			"com.amazons3.service",
})
public class AmazonS3FileCrudOperationsApplication {

	public static void main(String[] args) {
		SpringApplication.run(AmazonS3FileCrudOperationsApplication.class, args);
	}
	
	/*
	 * Note: 0

         If Your controller should be in the same package of SpringBoot App run then no need for the component Scan
         @SpringBootApplication will automatically identify
         if they are in different packages then we have to with ComponentScan for the spring boot to identify the beans
         
         And the order of Component bean declaration also imp
	 */

}
