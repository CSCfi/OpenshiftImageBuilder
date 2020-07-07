package fi.csc.notebooks.osbuilder.auth;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;

import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;

import java.util.Date;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import fi.csc.notebooks.osbuilder.constants.SecurityConstants;
import fi.csc.notebooks.osbuilder.models.ApplicationUser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class JWTAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
	
	private Gson gson;
	
    private AuthenticationManager authenticationManager;

    public JWTAuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
        this.gson = new Gson();
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest req,
                                                HttpServletResponse res) throws AuthenticationException {
		
    	
    	try {
            ApplicationUser creds = new ObjectMapper()
                    .readValue(req.getInputStream(), ApplicationUser.class);

            System.out.println(creds);
            return authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            creds.getUsername(),
                            creds.getPassword(),
                            new ArrayList<>())
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest req,
                                            HttpServletResponse res,
                                            FilterChain chain,
                                            Authentication auth) throws IOException, ServletException {

    	
    	
    	byte[] keyBytes = Files.readAllBytes(Paths.get(SecurityConstants.PRIVATE_KEY_DER_PATH));

        PKCS8EncodedKeySpec spec =
          new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory kf = null;
		try {
			kf = KeyFactory.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
         PrivateKey privateKey = null;
		try {
			privateKey = kf.generatePrivate(spec);
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
    	
		String username = ((User)auth.getPrincipal()).getUsername();
    	String token = Jwts.builder()
    	.setSubject(username)
    	.setExpiration(new Date(System.currentTimeMillis() + SecurityConstants.EXPIRATION_TIME))
    	//.signWith(SignatureAlgorithm.HS256, SecurityConstants.SECRET.getBytes())
    	.signWith(SignatureAlgorithm.RS512, privateKey)
    	.compact();
    	
    	
    	/*
        String token = JWT.create()
                .withSubject(((User) auth.getPrincipal()).getUsername())
                .withExpiresAt(new Date(System.currentTimeMillis() + SecurityConstants.EXPIRATION_TIME))
                .sign(HMAC512(SECRET.getBytes()));
                */
        res.addHeader(SecurityConstants.HEADER_STRING, SecurityConstants.TOKEN_PREFIX + token);
        
        res.setContentType("application/json");
        JsonObject userToken = new JsonObject();
        userToken.addProperty("token", token);
        userToken.addProperty("username", username);
        res.getWriter().write(gson.toJson(userToken));
    }
}