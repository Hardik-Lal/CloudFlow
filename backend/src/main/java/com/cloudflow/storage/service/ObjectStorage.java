package com.cloudflow.storage.service;

import java.io.InputStream;

/** The artifact bucket. */
public interface ObjectStorage {

  void put(String key, byte[] content, String contentType);

  /** Opens the object for reading; the caller closes the stream. */
  InputStream open(String key);

  /** Deletes every object whose key starts with {@code prefix}. */
  void deletePrefix(String prefix);
}
