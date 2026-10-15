package com.carlos.DocChat.AI.config.aws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Configuration
public class S3Config {
    private final S3Client s3Client;
    private final String bucket;

    public S3Config(S3Client s3Client, @Value("${app.aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Bean
    public static S3Client s3Client(@Value("${app.aws.s3.region}") String region,
                                    @Value("${app.aws.s3.profile}") String profile) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(ProfileCredentialsProvider.create(profile))
                .build();
    }
    public String getBucketName() {
        return bucket;
    }

    public void uploadPdf(String key, byte[] content, String contentType) {
        s3Client.putObject(PutObjectRequest.builder()
                .bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(content));
    }

    public byte[] downloadPdf(String key) {
        return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket).key(key).build()).asByteArray();
    }

    public void deletePdf(String key) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
