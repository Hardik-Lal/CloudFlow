package com.cloudflow.storage.service;

import java.io.InputStream;
import java.util.List;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Object;

/** {@link ObjectStorage} on Amazon S3 or an S3-compatible service. */
public class S3ObjectStorage implements ObjectStorage {

  private final S3Client s3;
  private final String bucket;

  public S3ObjectStorage(S3Client s3, String bucket) {
    this.s3 = s3;
    this.bucket = bucket;
  }

  @Override
  public void put(String key, byte[] content, String contentType) {
    s3.putObject(
        request -> request.bucket(bucket).key(key).contentType(contentType),
        RequestBody.fromBytes(content));
  }

  @Override
  public InputStream open(String key) {
    return s3.getObject(request -> request.bucket(bucket).key(key));
  }

  @Override
  public void deletePrefix(String prefix) {
    s3.listObjectsV2Paginator(request -> request.bucket(bucket).prefix(prefix)).stream()
        .forEach(
            page -> {
              List<ObjectIdentifier> keys =
                  page.contents().stream()
                      .map(S3Object::key)
                      .map(key -> ObjectIdentifier.builder().key(key).build())
                      .toList();
              if (!keys.isEmpty()) {
                s3.deleteObjects(
                    request ->
                        request.bucket(bucket).delete(Delete.builder().objects(keys).build()));
              }
            });
  }
}
