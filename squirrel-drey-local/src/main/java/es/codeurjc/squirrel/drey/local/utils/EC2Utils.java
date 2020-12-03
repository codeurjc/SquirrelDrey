package es.codeurjc.squirrel.drey.local.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class EC2Utils {

    private static final String EC2_METADATA_SERVICE_URL = "http://169.254.169.254";
    private static final String EC2_INSTANCE_ID_PATH = "/latest/meta-data/instance-id";
    private static final int TIMEOUT = 10000;

    public static String retrieveInstanceId() throws IOException {
        String EC2Id = null;
        String inputLine;
        URL EC2MetaData = new URL(EC2_METADATA_SERVICE_URL + EC2_INSTANCE_ID_PATH);
        URLConnection EC2MD = EC2MetaData.openConnection();
        EC2MD.setConnectTimeout(TIMEOUT);
        EC2MD.setReadTimeout(TIMEOUT);
        BufferedReader in = new BufferedReader(new InputStreamReader(EC2MD.getInputStream()));
        while ((inputLine = in.readLine()) != null) {
            EC2Id = inputLine;
        }
        in.close();
        return EC2Id;
    }
}
