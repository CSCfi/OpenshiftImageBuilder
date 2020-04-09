package fi.csc.notebooks.osbuilder.client;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import javax.xml.bind.ValidationException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import fi.csc.notebooks.osbuilder.misc.OSJsonParser;
import fi.csc.notebooks.osbuilder.models.BuildStatusImage;
import fi.csc.notebooks.osbuilder.utils.Utils;

@Service
public class OCRestClient {


	private RestTemplate rt;

	@Autowired
	public OCRestClient(){
		rt = new RestTemplate();	
	}

	private MultiValueMap<String, String> getHeaders() {

		MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
		Properties props = Utils.readProperties();
		headers.add("Authorization", "Bearer " + props.getProperty("TOKEN"));
		headers.add("Content-Type", "application/json");

		return headers;

	}

	public ResponseEntity<String> getBuildConfigs() {


		HttpEntity<String> entity = new HttpEntity<String>(this.getHeaders());

		String buildURL = Utils.generateOSUrl("oapi", "buildconfigs");

		System.out.println(buildURL);


		ResponseEntity<String> resp = rt.exchange(buildURL, 
				HttpMethod.GET, 
				entity, 
				String.class
				);



		return resp;

	}

	public ResponseEntity<String> getBuildConfig(String name) {


		HttpEntity<String> entity = new HttpEntity<String>(this.getHeaders());

		String buildURL = Utils.generateOSUrl("apis", "buildconfigs", name);

		System.out.println(buildURL);

		
	
		ResponseEntity<String> resp = rt.exchange(buildURL, 
				HttpMethod.GET, 
				entity, 
				String.class
				);
		return resp;

	}
	
	//  https://rahti.csc.fi:8443/apis/build.openshift.io/v1/namespaces/pebbles/builds?labelSelector=buildconfig
	public ResponseEntity<String> getBuilds(String buildId) {


		HttpEntity<String> entity = new HttpEntity<String>(this.getHeaders());

		String buildURL = Utils.generateOSUrl("apis", "builds") + "?labelSelector={label}";

		System.out.println(buildURL);


		ResponseEntity<String> resp = rt.exchange(buildURL, 
				HttpMethod.GET, 
				entity, 
				String.class,
				String.format("buildconfig=%s", buildId)
				);



		return resp;

	}

	public ResponseEntity<BuildStatusImage> getBuildsStatus(String buildId) {


		HttpEntity<String> entity = new HttpEntity<String>(this.getHeaders());

		String buildURL = Utils.generateOSUrl("apis", "builds") + "?labelSelector={label}";

		ResponseEntity<String> resp = rt.exchange(buildURL, 
				HttpMethod.GET, 
				entity, 
				String.class,
				String.format("buildconfig=%s", buildId)
				);

		
		BuildStatusImage bsi = OSJsonParser.parseBuildList(resp.getBody());
		return new ResponseEntity<BuildStatusImage>(bsi,HttpStatus.OK);

	}

	public ResponseEntity<List<String>> getImageStreams() {


		HttpEntity<String> entity = new HttpEntity<String>(this.getHeaders());

		String imageStreamURL = Utils.generateOSUrl("apis", "imagestreams");

		ResponseEntity<String> resp = rt.exchange(
				imageStreamURL, 
				HttpMethod.GET, 
				entity,
				String.class
				);

		if (resp.getStatusCode().is2xxSuccessful())
			return new ResponseEntity<List<String>>(
					OSJsonParser.parseImageStream(resp.getBody()), 
					HttpStatus.OK);

		
		List<String> error_resp_list = new LinkedList<String>();
		error_resp_list.add(resp.getBody());
		return new ResponseEntity<List<String>>(error_resp_list, resp.getStatusCode());

	}

	public ResponseEntity<List<String>> getImageStream(String imageName) {


		HttpEntity<String> entity = new HttpEntity<String>(this.getHeaders());

		String imageStreamURL = Utils.generateOSUrl("apis", "imagestreams", imageName);

		//String imageStreamURL = Utils.generateOSUrl("apis", "imagestreams") + "?labelSelector={label}";

		System.out.println(imageStreamURL);

		ResponseEntity<String> resp = rt.exchange(
				imageStreamURL, 
				HttpMethod.GET, 
				entity, 
				String.class
				//String.format("build=%s", imageName)
				);

		if (resp.getStatusCode().is2xxSuccessful())
			return new ResponseEntity<List<String>>(
					OSJsonParser.parseImageStream(resp.getBody()), 
					HttpStatus.OK);

		List<String> error_resp_list = new LinkedList<String>();
		error_resp_list.add(resp.getBody());
		return new ResponseEntity<List<String>>(error_resp_list, resp.getStatusCode());

	}


	public ResponseEntity<String> postBuildConfig(
			String hash, 
			String uri, 
			Optional<String> branch, 
			Optional<String> contextDir) throws URISyntaxException {

		String buildURL = Utils.generateOSUrl("oapi", "buildconfigs");

		if(uri.isEmpty())
			return new ResponseEntity<String>(HttpStatus.UNPROCESSABLE_ENTITY);

		RequestEntity<String> e = new RequestEntity<String>(
				OSJsonParser.getPOSTBody("BuildConfig", hash, uri, branch, contextDir).toString(),
				this.getHeaders(), 
				HttpMethod.POST, 
				new URI(buildURL) 
				); 

		ResponseEntity<String> resp = rt.exchange(e, String.class);

		return resp;
	}

	/**
	 * This method should be used, only if the build hasn't been triggered 
	 * or the build pods have been deleted
	 * @param params : Make sure the 'name' key exists
	 * @return response
	 * @throws URISyntaxException 
	 */
	public ResponseEntity<String> deleteBuildConfig(String hash) throws URISyntaxException{

		String buildURL = Utils.generateOSUrl("oapi", "buildconfigs");

		URI buildDeleteURL = new URI(buildURL + "/" + hash);

		RequestEntity<String> e = new RequestEntity<String>(
				this.getHeaders(), 
				HttpMethod.DELETE, 
				buildDeleteURL
				);

		ResponseEntity<String> resp = rt.exchange(e, String.class);

		return resp;
	}

	/** The build needs an image stream as well to output the final image
	 * @param params (name)
	 * @return
	 * @throws URISyntaxException
	 */
	public ResponseEntity<String> postImageStreamConfig(String hash) throws URISyntaxException {


		String imageStreamURL = Utils.generateOSUrl("apis", "imagestreams");


		System.out.println(imageStreamURL);


		if(hash.isEmpty())
			throw new RuntimeException("The parameter hash is missing");

		RequestEntity<String> e = new RequestEntity<String>(
				OSJsonParser.getPOSTBody("ImageStream", hash).toString(),
				this.getHeaders(), 
				HttpMethod.POST, 
				new URI(imageStreamURL) 
				); 

		ResponseEntity<String> resp = rt.exchange(e, String.class);

		return resp;
	}


	public ResponseEntity<String> postBuildRequest(String hash) throws URISyntaxException, ValidationException{

		String buildRequestURL = Utils.generateOSUrl("apis", "buildconfigs", hash) + "/instantiate";

		RequestEntity<String> e = new RequestEntity<String>(
				OSJsonParser.getPOSTBody("BuildRequest", hash).toString(),
				this.getHeaders(), 
				HttpMethod.POST, 
				new URI(buildRequestURL) 
				);

		ResponseEntity<String> resp = rt.exchange(e, String.class);

		return resp;

	}

}


