package fi.csc.notebooks.osbuilder.controller;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;

import ch.qos.logback.classic.Level;
import fi.csc.notebooks.osbuilder.client.OCRestClient;
import fi.csc.notebooks.osbuilder.models.BuildStatusImage;
import fi.csc.notebooks.osbuilder.utils.Utils;

@RestController
@CrossOrigin
@RequestMapping(value="/api")
public class OSController {

	
	@Autowired
	OCRestClient client;
	
	private static final Logger logger = LoggerFactory.getLogger(OSController.class);
	ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(logger.getName());
	
	@PostConstruct
	public void initialize() {
		if(Utils.getDebugState())
    		root.setLevel(Level.DEBUG);
	}
	
	@GetMapping("/buildconfigs")
	ResponseEntity<String> getBuildConfig(
			@RequestParam Optional<String> url,
			@RequestParam Optional<String> branch,
			@RequestParam Optional<String> contextDir,
			@RequestParam Optional<String> dockerfilePath) {
		
		
		if(url.isPresent()){
			String uri  = url.get();
			String hash = Utils.generateHash(uri, branch, contextDir, dockerfilePath);
			logger.info("Getting BuildConfig for " + hash);
			return client.getBuildConfig(hash);
		}
		logger.info("Getting all BuildConfigs available");
		return client.getBuildConfigs();
		
	}
	
	
	@GetMapping("/images")
	ResponseEntity<List<Map<String,String>>> getImageStreams() {
		logger.info("Getting all ImageStreams available");
		return client.getImageStreams();
	}
	
	@GetMapping("/image")
	ResponseEntity<Map<String,String>> getImageStream(
			@RequestParam String url,
			@RequestParam Optional<String> branch,
			@RequestParam Optional<String> contextDir,
			@RequestParam Optional<String> dockerfilePath) {
		
			String hash = Utils.generateHash(url, branch, contextDir, dockerfilePath);
			logger.info("Getting ImageStream for " + hash);
			return client.getImageStream(hash);
	}
	
	/*
	@GetMapping("/images/{buildId}")
	ResponseEntity<String> getImageStream(@PathVariable String buildId) {
		
		return client.getImageStream(buildId);
		
	}
	*/
	
	@GetMapping("/build/{buildId}")
	ResponseEntity<String> getBuilds(@PathVariable String buildId) { // Not used, mainly for future development, so return String for now
		
		return client.getBuilds(buildId);
		
	}
	
	@GetMapping("/build/status/{buildConfigName}")
	ResponseEntity<BuildStatusImage> getBuildsStatus(@PathVariable String buildConfigName) {
		
		return client.getBuildStatus(buildConfigName);
		
	}
	
	@GetMapping("/build/logs/{buildName}")
	ResponseEntity<String> getBuildLogs(@PathVariable String buildName) {
		
		return client.getBuildLogs(buildName);
		
	}
	
	
	
	@PostMapping("/buildconfig")
	ResponseEntity<String> postBuildConfig(
			@RequestParam String url,
			@RequestParam Optional<String> branch,
			@RequestParam Optional<String> contextDir,
			@RequestParam Optional<String> dockerfilePath // Special case: Generate the image using dockerfile (no s2i)
			) throws URISyntaxException{
		
		String hash = Utils.generateHash(url, branch, contextDir, dockerfilePath);
		
		ResponseEntity<String> build_resp = client.postBuildConfig(hash, url, branch, contextDir, dockerfilePath); // Create BuildConfig object
		logger.debug(build_resp.getStatusCodeValue() + " -- " + build_resp.getBody());
		
		if(!build_resp.getStatusCode().is2xxSuccessful())
			return build_resp;
		
		/* BuildConfig and ImageStream objects go hand in hand, and hence should be created together 
		 * If BuildConfig creation succeeds but ImageStream object creation fails, then simply delete the created BuildConfig object
		 */
		
		try {
		
			ResponseEntity<String> image_resp = client.postImageStreamConfig(hash); // Create ImageStream object
			logger.debug(image_resp.getStatusCodeValue() + " -- " + image_resp.getBody());
			if (!image_resp.getStatusCode().is2xxSuccessful())
				return image_resp;
			
		}
		catch (HttpClientErrorException ie) {
			logger.error(ie.getMessage());
			/* 409: CONFLICT error message generally means that the ImageStream object already exits
			 * In this case do not delete the created BuildConfig object. 
			 */
			if (ie.getRawStatusCode() != 409) { // For every other case, delete the created BuildConfig object
				try {
					logger.warn("Error creating ImageStream, deleting associated BuildConfig : " + hash);
					client.deleteBuildConfig(hash); // Backtrack the created BuildConfig object
				}
				catch (HttpClientErrorException de) {
					logger.error(de.getMessage());
					return new ResponseEntity<String>(de.getMessage(), HttpStatus.valueOf(de.getRawStatusCode()));
				}
				
				return new ResponseEntity<String>(ie.getMessage(), ie.getStatusCode());
			}
		}
		
		logger.info("BuildConfig and ImageStream created for " + hash);
		ResponseEntity<String> result = new ResponseEntity<String>(hash, HttpStatus.CREATED); // OK
		return result;
	}
	
	
	
	@PostMapping("/build/{buildConfigName}")
	ResponseEntity<String> startBuild(@PathVariable String buildConfigName) throws URISyntaxException{
		
		logger.info("Build started for : " + buildConfigName);
		return client.postBuildRequest(buildConfigName);
		
		
	}
	
	@DeleteMapping("/buildconfig/{buildConfigName}")
	ResponseEntity<String> deleteBuildConfig(@PathVariable String buildConfigName) throws URISyntaxException{
		
		ResponseEntity<String> resp = client.deleteBuildConfig(buildConfigName);
		logger.info("BuildConfig deleted : " + buildConfigName);
		
		return resp;
	}
	
	
	@DeleteMapping("/build/{buildName}")
	ResponseEntity<String> deleteBuild(@PathVariable String buildName) throws URISyntaxException{
		
		ResponseEntity<String> resp = client.deleteBuild(buildName);
		logger.info("Build deleted : " + buildName);
		
		return resp;
	}
	
	@DeleteMapping("/builds/{buildConfigName}")
	ResponseEntity<String> deleteBuilds(@PathVariable String buildConfigName) throws URISyntaxException{
		
		ResponseEntity<String> resp = client.deleteAllBuilds(buildConfigName);
		logger.info("All Builds deleted for BuildConfig: " + buildConfigName);
		
		return resp;
	}
	
	@DeleteMapping("/image/{buildConfigName}")
	ResponseEntity<String> deleteImage(@PathVariable String buildConfigName) throws URISyntaxException{
		
		ResponseEntity<String> resp = client.deleteImage(buildConfigName);
		logger.info("ImageStream deleted for BuildConfig: " + buildConfigName);
		
		return resp;
	}
	
		
}