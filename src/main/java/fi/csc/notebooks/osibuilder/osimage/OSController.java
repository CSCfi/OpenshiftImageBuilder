package fi.csc.notebooks.osibuilder.osimage;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.xml.bind.ValidationException;

import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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

import fi.csc.notebooks.osbuilder.client.OCRestClient;
import fi.csc.notebooks.osbuilder.utils.Utils;

@SpringBootApplication(scanBasePackages = {
"fi.csc.notebooks.osbuilder.client"})
@RestController
@CrossOrigin
@RequestMapping(value="/api")
public class OSController {

	
	@Autowired
	OCRestClient client;
	
	
	@GetMapping("/builds")
	ResponseEntity<String> getBuild(
			@RequestParam Optional<String> url,
			@RequestParam Optional<String> branch,
			@RequestParam Optional<String> contextDir) {
		
		
		if(url.isPresent()){
			String uri  = url.get();
			String hash = Utils.generateHash(uri, branch, contextDir);
			return client.getBuildConfig(hash);
		}
		return client.getBuildConfigs();
		
	}
	
	
	@GetMapping("/images")
	ResponseEntity<List<String>> getImageStream(
			@RequestParam Optional<String> url,
			@RequestParam Optional<String> branch,
			@RequestParam Optional<String> contextDir) {
		
		
		if(url.isPresent()){
			String uri  = url.get();
			String hash = Utils.generateHash(uri, branch, contextDir);
			return client.getImageStream(hash);
		}
		
		return client.getImageStreams();
	}
	
	
	/*
	@GetMapping("/images/{buildId}")
	ResponseEntity<String> getImageStream(@PathVariable String buildId) {
		
		return client.getImageStream(buildId);
		
	}
	*/
	
	@PostMapping("/builds")
	ResponseEntity<String> postBuild(@RequestParam String url,
			@RequestParam Optional<String> branch,
			@RequestParam Optional<String> contextDir
			) throws URISyntaxException{
		
		String hash = Utils.generateHash(url, branch, contextDir);
		
		ResponseEntity<String> build_resp = client.postBuildConfig(hash, url, branch, contextDir);
		if (!build_resp.getStatusCode().is2xxSuccessful()) // Error
			return build_resp;
		
		ResponseEntity<String> image_resp = client.postImageStreamConfig(hash);
		if (!image_resp.getStatusCode().is2xxSuccessful())  // Error
		{
			try {
			client.deleteBuildConfig(hash); // Backtrack the created build config object
			}
			catch (Exception e) {
				return new ResponseEntity<String>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
			}
			return image_resp;
		}
		
		
		ResponseEntity<String> result = new ResponseEntity<String>(HttpStatus.CREATED); // OK
		return result;
	}
	
	@PostMapping("/builds/start/{buildName}")
	ResponseEntity<String> startBuild(@PathVariable String buildName) throws URISyntaxException{
		
		
		ResponseEntity<String> result;
		try {
			result = client.postBuildRequest(buildName);
		} catch (ValidationException e) {
			// TODO Auto-generated catch block
			return new ResponseEntity<String>(e.getMessage(), HttpStatus.NOT_ACCEPTABLE);
		}
		
		return result;
		
		
	}
	
	@DeleteMapping("/builds/delete/{buildName}")
	ResponseEntity<String> deleteBuild(@PathVariable String buildName) throws URISyntaxException{
		
		ResponseEntity<String> resp = client.deleteBuildConfig(buildName);
		
		return resp;
	}
		
		
}
