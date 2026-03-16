package com.bondhub.common.utils;

public class S3Util {
    public static String getS3BaseUrl(String bucketName, String region) {
        return String.format("https://%s.s3.%s.amazonaws.com/", bucketName, region);
    }
}
