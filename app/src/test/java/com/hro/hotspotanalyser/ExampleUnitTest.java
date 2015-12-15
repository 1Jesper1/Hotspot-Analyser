package com.hro.hotspotanalyser;

import org.junit.Test;

import java.lang.Exception;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.HttpsURLConnection;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

/**
 * To work on unit tests, switch the Test Artifact in the Build Variants view.
 */
public class ExampleUnitTest {
    @Test
    //Test should fail because of HTTP url cast to HTTPS url connection
    public void certificateCheckHTTP() throws Exception   {
                try {
                    assertEquals(X509Certificate[].class, getCertificates("http://google.com/").getClass());
                    fail("Should have thrown an exception");
                }
                catch(Exception e){
                    assertEquals("sun.net.www.protocol.http.HttpURLConnection cannot be cast to javax.net.ssl.HttpsURLConnection", e.getMessage());
                }
    }
    @Test
    //Test should pass, can only fail when there is no internet connection or website is offline
    public void certificateCheckHTTPS() throws Exception {
                try {
                    assertEquals(X509Certificate[].class, getCertificates("https://google.com/").getClass());
                }
                catch(Exception e){
                    fail("Should not thrown an exception, maybe there is no internet connection");
                }
    }
    private X509Certificate[] getCertificates(String portalUrl) throws Exception{
        HttpsURLConnection conn = null;
            try {

                URL url = new URL(portalUrl);
                conn = (HttpsURLConnection) url.openConnection();
                conn.getInputStream();

                //Get server certificates
                Certificate[] certificates = conn.getServerCertificates();
                return Arrays.copyOf(certificates, certificates.length, X509Certificate[].class);
            } catch (Exception e) {
                throw new Exception(e.getMessage());
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
    }
}